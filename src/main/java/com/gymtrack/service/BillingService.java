package com.gymtrack.service;

import com.gymtrack.model.Gym;
import com.gymtrack.model.Payment;
import com.gymtrack.model.User;
import com.gymtrack.repository.GymRepository;
import com.gymtrack.repository.PaymentRepository;
import com.gymtrack.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

// Cobranza de mensualidades.
//
// Regla acordada: si pagas el 20, tu mensualidad cubre hasta el 20 del mes
// siguiente. El día 21 (el primero después de la fecha de corte) la membresía
// pasa a "inactive" sola, sin que el dueño tenga que acordarse.
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

    // Registra un pago y empuja la fecha de corte un mes hacia adelante.
    public Payment registrarPago(User member, String gymId, Double monto, String metodo,
                                 LocalDate fechaPago, String nota) {
        LocalDate pago = fechaPago == null ? LocalDate.now() : fechaPago;

        // El día de pago se fija con el primer abono y ya no se mueve: si pagaste
        // el 20, tu corte siempre cae en 20, aunque un mes pagues tarde.
        if (member.getDiaDePago() == null) {
            member.setDiaDePago(pago.getDayOfMonth());
        }

        // Si todavía tiene saldo a favor, el mes nuevo se encadena al corte vigente
        // en vez de regalarle días o quitárselos por pagar antes de tiempo.
        LocalDate base = (member.getFechaProximoPago() != null && member.getFechaProximoPago().isAfter(pago))
                ? member.getFechaProximoPago()
                : pago;
        LocalDate cubreHasta = sumarUnMes(base, member.getDiaDePago());

        member.setFechaProximoPago(cubreHasta);
        member.setMembershipStatus(User.STATUS_ACTIVE);
        userRepository.save(member);

        Payment payment = new Payment();
        payment.setGymId(gymId);
        payment.setUserId(member.getId());
        payment.setMonto(monto);
        payment.setMetodo(metodo);
        payment.setFechaPago(pago);
        payment.setCubreHasta(cubreHasta);
        payment.setNota(nota);
        return paymentRepository.save(payment);
    }

    // Suma un mes respetando el día de pago. Si ese día no existe en el mes
    // destino (pagó un 31 y el mes siguiente tiene 30), cae en el último día.
    private LocalDate sumarUnMes(LocalDate desde, Integer diaDePago) {
        LocalDate siguiente = desde.plusMonths(1);
        if (diaDePago == null) return siguiente;
        int dia = Math.min(diaDePago, siguiente.lengthOfMonth());
        return siguiente.withDayOfMonth(dia);
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
