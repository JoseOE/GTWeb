package com.gymtrack.service;

import com.gymtrack.model.Gym;
import com.gymtrack.model.Payment;
import com.gymtrack.model.User;
import com.gymtrack.repository.GymRepository;
import com.gymtrack.repository.PaymentRepository;
import com.gymtrack.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;

// Cobranza de membresías.
//
// Regla acordada: si pagas el 20, tu mensualidad cubre hasta el 20 del mes
// siguiente. El día 21 (el primero después de la fecha de corte) la membresía
// pasa a "inactive" sola, sin que el dueño tenga que acordarse. Los planes de
// varios meses (trimestral, anual...) siguen la misma regla; los de días o
// semanas (visita, semana) solo suman días, y si después paga un mes, ese mes
// empieza cuando termina lo que ya había pagado.
@Service
public class BillingService {

    private static final Logger log = LoggerFactory.getLogger(BillingService.class);
    // Cuántos días antes del corte se avisa al usuario.
    private static final int DIAS_DE_AVISO = 5;

    private final UserRepository userRepository;
    private final GymRepository gymRepository;
    private final PaymentRepository paymentRepository;
    private final PushService pushService;
    private final CorreoService correoService;

    public BillingService(UserRepository userRepository, GymRepository gymRepository,
                          PaymentRepository paymentRepository, PushService pushService,
                          CorreoService correoService) {
        this.userRepository = userRepository;
        this.gymRepository = gymRepository;
        this.paymentRepository = paymentRepository;
        this.pushService = pushService;
        this.correoService = correoService;
    }

    // Pago registrado a mano sin elegir plan: cubre un mes, como siempre.
    public Payment registrarPago(User member, String gymId, Double monto, String metodo,
                                 LocalDate fechaPago, String nota) {
        return registrarPago(member, gymId, monto, metodo, fechaPago, nota, PlanPagado.MENSUAL, null);
    }

    // Registra un pago y empuja la fecha de corte según la duración del plan.
    //
    // Con orderId (compra pagada en la tienda) es idempotente: el pago se
    // inserta primero y el índice único de orderId hace que, si el mismo pedido
    // se confirma dos veces (dos peticiones a la vez), solo la primera extiende
    // la membresía. La segunda recibe el pago que ya existía.
    public Payment registrarPago(User member, String gymId, Double monto, String metodo,
                                 LocalDate fechaPago, String nota, PlanPagado plan, String orderId) {
        LocalDate pago = fechaPago == null ? LocalDate.now() : fechaPago;
        PlanPagado duracion = plan == null ? PlanPagado.MENSUAL : plan;

        Payment payment = new Payment();
        payment.setGymId(gymId);
        payment.setUserId(member.getId());
        payment.setMonto(monto);
        payment.setMetodo(metodo);
        payment.setFechaPago(pago);
        payment.setNota(nota);
        payment.setPlanId(duracion.planId());
        payment.setPlan(duracion.nombre());
        payment.setDuracionUnidad(duracion.unidad());
        payment.setDuracionCantidad(duracion.cantidad());
        payment.setOrderId(orderId);
        if (orderId != null) {
            try {
                payment = paymentRepository.insert(payment);
            } catch (DuplicateKeyException yaAplicado) {
                log.info("El pedido {} ya había extendido la membresía de {}; se ignora el repetido.", orderId, member.getEmail());
                return paymentRepository.findByOrderId(orderId).orElseThrow();
            }
        }

        // Si todavía tiene saldo a favor, el plan nuevo se encadena al corte vigente
        // en vez de regalarle días o quitárselos por pagar antes de tiempo.
        boolean conSaldo = member.getFechaProximoPago() != null && member.getFechaProximoPago().isAfter(pago);
        LocalDate base = conSaldo ? member.getFechaProximoPago() : pago;

        // El día de pago se fija con el primer abono mensual y ya no se mueve: si
        // pagaste el 20, tu corte siempre cae en 20, aunque un mes pagues tarde.
        // Una visita o una semana no lo fijan. Si al pagar el mes todavía le queda
        // una visita o una semana, el mes empieza cuando termina lo que ya pagó y
        // el día de pago pasa a ser ese (regla acordada: "al terminar lo pagado").
        if (duracion.esPorMes()) {
            if (member.getDiaDePago() == null) {
                member.setDiaDePago(base.getDayOfMonth());
            } else if (conSaldo && !esDiaDeCorte(base, member.getDiaDePago())) {
                member.setDiaDePago(base.getDayOfMonth());
            }
        }
        LocalDate cubreHasta = extender(base, duracion, member.getDiaDePago());

        payment.setCorteAnterior(member.getFechaProximoPago());
        member.setFechaProximoPago(cubreHasta);
        member.setMembershipStatus(User.STATUS_ACTIVE);
        if (duracion.nombre() != null) member.setPlanActual(duracion.nombre());
        userRepository.save(member);

        payment.setCubreHasta(cubreHasta);
        return paymentRepository.save(payment);
    }

    // Los planes por mes respetan el día de pago; los de días o semanas suman días.
    private LocalDate extender(LocalDate desde, PlanPagado plan, Integer diaDePago) {
        return switch (plan.unidad()) {
            case "dia" -> desde.plusDays(plan.cantidad());
            case "semana" -> desde.plusWeeks(plan.cantidad());
            default -> sumarMeses(desde, plan.cantidad(), diaDePago);
        };
    }

    // Suma meses respetando el día de pago. Si ese día no existe en el mes
    // destino (pagó un 31 y el mes siguiente tiene 30), cae en el último día.
    private LocalDate sumarMeses(LocalDate desde, int meses, Integer diaDePago) {
        LocalDate siguiente = desde.plusMonths(meses);
        if (diaDePago == null) return siguiente;
        int dia = Math.min(diaDePago, siguiente.lengthOfMonth());
        return siguiente.withDayOfMonth(dia);
    }

    // ¿Esa fecha es un corte normal para ese día de pago? (El 30 de noviembre
    // lo es para quien paga los 31.) Si no, el corte vino de una visita o semana.
    private static boolean esDiaDeCorte(LocalDate fecha, int diaDePago) {
        return fecha.getDayOfMonth() == Math.min(diaDePago, fecha.lengthOfMonth());
    }

    // Qué le pasó a la membresía al reembolsar el pedido que la había extendido.
    public record Reversion(String miembro, boolean ajustada, LocalDate vence) {}

    // Reembolso de una compra de la tienda que extendió la membresía: el pago
    // queda marcado y, si fue el último que la extendió, la fecha de corte
    // vuelve a la que tenía antes de pagarlo (o al día del pago si no tenía
    // ninguna). Si después hubo otro pago, la fecha no se toca: el dueño la
    // revisa en Membresías y Pagos. Se puede llamar más de una vez.
    public Reversion revertirPagoDePedido(String orderId) {
        Payment pago = paymentRepository.findByOrderId(orderId).orElse(null);
        if (pago == null) return null;
        User member = userRepository.findById(pago.getUserId()).orElse(null);
        if (pago.getReembolsadoEn() != null || member == null) {
            return member == null ? null : new Reversion(member.getNombre(), false, member.getFechaProximoPago());
        }
        pago.setReembolsadoEn(Instant.now());
        paymentRepository.save(pago);
        if (!Objects.equals(member.getFechaProximoPago(), pago.getCubreHasta())) {
            log.info("Pedido {} reembolsado: {} tiene pagos posteriores y su membresía no se ajustó.", orderId, member.getEmail());
            return new Reversion(member.getNombre(), false, member.getFechaProximoPago());
        }
        LocalDate vence = corteAntesDe(pago);
        // Si ese corte lo había dado otro pago que también se reembolsó, se
        // sigue hacia atrás hasta el último que sí se pagó. Se detiene en cuanto
        // un pago vigente explica el corte.
        List<Payment> suyos = paymentRepository.findByUserIdOrderByFechaPagoDesc(member.getId());
        for (int i = 0; i < suyos.size(); i++) {
            LocalDate corte = vence;
            boolean loDaUnPagoVigente = suyos.stream()
                    .anyMatch(x -> x.getReembolsadoEn() == null && !x.getId().equals(pago.getId()) && Objects.equals(x.getCubreHasta(), corte));
            Payment previo = loDaUnPagoVigente ? null : suyos.stream()
                    .filter(x -> x.getReembolsadoEn() != null && !x.getId().equals(pago.getId()) && Objects.equals(x.getCubreHasta(), corte))
                    .findFirst().orElse(null);
            if (previo == null) break;
            vence = corteAntesDe(previo);
        }
        member.setFechaProximoPago(vence);
        if (vence.isBefore(LocalDate.now()) && User.STATUS_ACTIVE.equals(member.getMembershipStatus())) {
            member.setMembershipStatus(User.STATUS_INACTIVE);
        }
        userRepository.save(member);
        log.info("Pedido {} reembolsado: la membresía de {} vuelve a vencer el {}.", orderId, member.getEmail(), vence);
        return new Reversion(member.getNombre(), true, vence);
    }

    // El corte que tenía el miembro antes de ese pago; si no tenía ninguno, el
    // día en que pagó (la membresía no queda abierta sin fecha).
    private static LocalDate corteAntesDe(Payment pago) {
        return pago.getCorteAnterior() != null ? pago.getCorteAnterior() : pago.getFechaPago();
    }

    // Corre todos los días a las 6:00 de la mañana: da de baja a los vencidos y
    // avisa a quienes les faltan 5 días. Es lo que hace que la baja sea automática
    // aunque nadie abra el panel web.
    @Scheduled(cron = "0 0 6 * * *")
    public void revisarMembresias() {
        LocalDate hoy = LocalDate.now();
        List<User> miembros = userRepository.findByRoleAndFechaProximoPagoNotNull("member");
        int dadosDeBaja = 0, avisados = 0;

        for (User member : miembros) {
            Long dias = member.diasParaVencer(hoy);
            if (dias == null) continue;

            if (dias < 0 && User.STATUS_ACTIVE.equals(member.getMembershipStatus())) {
                member.setMembershipStatus(User.STATUS_INACTIVE);
                userRepository.save(member);
                dadosDeBaja++;
                pushService.enviar(member, "Tu membresía venció",
                        nombreGym(member) + " pausó tu acceso porque no se registró tu pago. Ponte al corriente para recuperarlo.",
                        Map.of("tipo", "membresia_vencida"));
                correoService.membresiaVencida(member, nombreGym(member));
            } else if (dias == DIAS_DE_AVISO && User.STATUS_ACTIVE.equals(member.getMembershipStatus())) {
                avisados++;
                pushService.enviar(member, "Tienes 5 días para pagar",
                        "Tu mensualidad en " + nombreGym(member) + " vence el " + member.getFechaProximoPago() + ".",
                        Map.of("tipo", "recordatorio_pago", "dias", dias));
                correoService.pagoPorVencer(member, nombreGym(member), member.getFechaProximoPago(), dias);
            }
        }
        log.info("Revisión de membresías: {} dadas de baja, {} avisadas de {} revisadas.",
                dadosDeBaja, avisados, miembros.size());
    }

    private String nombreGym(User member) {
        if (member.getGymId() == null) return "Tu gimnasio";
        return gymRepository.findById(member.getGymId()).map(Gym::getNombre).orElse("Tu gimnasio");
    }
}
