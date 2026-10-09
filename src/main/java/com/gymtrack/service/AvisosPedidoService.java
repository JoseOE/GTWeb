package com.gymtrack.service;

import com.gymtrack.model.CorreoEnviado;
import com.gymtrack.model.Gym;
import com.gymtrack.model.Payment;
import com.gymtrack.model.Pedido;
import com.gymtrack.model.User;
import com.gymtrack.repository.CorreoEnviadoRepository;
import com.gymtrack.repository.GymRepository;
import com.gymtrack.repository.UserRepository;
import com.gymtrack.util.MetodosPago;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;

// Correos (con el PDF adjunto) y push que acompañan a cada pedido y a cada
// mensualidad registrada:
//   - compra pagada (tienda, app o mostrador) → "compra confirmada" + recibo;
//   - pedido Paynet pendiente → la ficha para pagar en tienda;
//   - pago Paynet capturado → "recibimos tu pago" + recibo + push;
//   - mensualidad registrada a mano → recibo de membresía.
//
// Corren en segundo plano: generar el PDF y hablar con Brevo no debe frenar
// el checkout ni el cobro en el mostrador. Cada correo automático se aparta antes de
// enviarse (colección correos_enviados), así que sale una sola vez aunque el
// pedido se confirme varias veces. Solo se escribe a miembros: en una venta
// de mostrador al público en general no hay a quién.
@Service
public class AvisosPedidoService {

    private static final Logger log = LoggerFactory.getLogger(AvisosPedidoService.class);
    private static final DateTimeFormatter VENCE = DateTimeFormatter.ofPattern("d 'de' MMMM, HH:mm 'h'", Locale.forLanguageTag("es-MX"));
    private static final ZoneId ZONA_MX = ZoneId.of("America/Mexico_City");

    private final ReciboService recibos;
    private final CorreoService correos;
    private final PushService push;
    private final UserRepository userRepository;
    private final GymRepository gymRepository;
    private final CorreoEnviadoRepository enviados;

    public AvisosPedidoService(ReciboService recibos, CorreoService correos, PushService push, UserRepository userRepository,
                               GymRepository gymRepository, CorreoEnviadoRepository enviados) {
        this.recibos = recibos;
        this.correos = correos;
        this.push = push;
        this.userRepository = userRepository;
        this.gymRepository = gymRepository;
        this.enviados = enviados;
    }

    // Lo llama PedidoService cada vez que un pedido cambia de estado (se crea,
    // se paga una ficha). vista es la forma del pedido que ve el comprador.
    @Async
    public void alActualizar(Pedido p, Map<String, Object> vista) {
        try {
            User comprador = comprador(p);
            if (comprador == null) return;
            boolean esPaynet = MetodosPago.PAYNET.equals(p.getProveedorPago());

            if (Pedido.ESTADO_PENDIENTE_PAGO.equals(p.getEstado()) && esPaynet && apartar("ficha:" + p.getOrderId())) {
                correos.fichaPaynet(comprador, nombreGym(p.getGymId()), vista,
                        PedidoService.referenciaLegible(String.valueOf(p.getDatosPago().get("referencia"))),
                        vence(p), recibos.fichaPaynet(p));
            }
            if (Pedido.ESTADO_PAGADO.equals(p.getEstado())) {
                if (esPaynet && apartar("pago-paynet:" + p.getOrderId())) {
                    correos.pagoPaynetRecibido(comprador, nombreGym(p.getGymId()), vista, recibos.reciboPedido(p));
                    push.enviar(comprador, "Recibimos tu pago",
                            nombreGym(p.getGymId()) + " recibió tu pago del pedido #" + p.getFolio() + ". Ya puedes pasar por él.",
                            Map.of("tipo", "pago_recibido", "orderId", p.getOrderId()));
                } else if (!esPaynet && apartar("compra:" + p.getOrderId())) {
                    correos.compraConfirmada(comprador, nombreGym(p.getGymId()), vista, recibos.reciboPedido(p));
                }
            }
        } catch (Exception e) {
            log.warn("No se pudieron enviar los avisos del pedido {}: {}", p.getOrderId(), e.getMessage());
        }
    }

    // Recibo de una mensualidad registrada a mano desde el panel.
    @Async
    public void pagoRegistrado(Payment pago) {
        try {
            if (pago.getOrderId() != null || !apartar("mensualidad:" + pago.getId())) return;
            enviarReciboDePago(pago);
        } catch (Exception e) {
            log.warn("No se pudo enviar el recibo del pago {}: {}", pago.getId(), e.getMessage());
        }
    }

    // ─── Reenvíos pedidos desde el panel o "Mis pedidos" (no se apartan) ───

    public boolean reenviarPedido(Pedido p, Map<String, Object> vista) {
        User comprador = comprador(p);
        if (comprador == null) {
            throw new TiendaException(HttpStatus.BAD_REQUEST, "Esta venta fue al público en general: no hay correo al cual enviarla.");
        }
        if (Pedido.ESTADO_PENDIENTE_PAGO.equals(p.getEstado()) && MetodosPago.PAYNET.equals(p.getProveedorPago())) {
            return correos.fichaPaynet(comprador, nombreGym(p.getGymId()), vista,
                    PedidoService.referenciaLegible(String.valueOf(p.getDatosPago().get("referencia"))), vence(p), recibos.fichaPaynet(p));
        }
        return correos.compraConfirmada(comprador, nombreGym(p.getGymId()), vista, recibos.reciboPedido(p));
    }

    public boolean enviarReciboDePago(Payment pago) {
        User member = pago.getUserId() == null ? null : userRepository.findById(pago.getUserId()).orElse(null);
        if (member == null) throw new TiendaException(HttpStatus.BAD_REQUEST, "El pago no tiene un miembro al cual enviarlo.");
        String concepto = pago.getPlan() != null ? "el plan " + pago.getPlan() : "tu mensualidad";
        String folio = pago.getId().substring(Math.max(0, pago.getId().length() - 8)).toUpperCase();
        return correos.reciboMensualidad(member, nombreGym(pago.getGymId()), concepto,
                pago.getMonto() == null ? 0 : pago.getMonto(), pago.getCubreHasta(), folio, recibos.reciboPago(pago));
    }

    // Inserta la marca del correo; si ya existía, otro hilo ya lo envió.
    private boolean apartar(String clave) {
        try {
            enviados.insert(new CorreoEnviado(clave));
            return true;
        } catch (DuplicateKeyException yaEnviado) {
            return false;
        }
    }

    private User comprador(Pedido p) {
        if (p.getUserId() == null) return null;
        return userRepository.findById(p.getUserId()).orElse(null);
    }

    private String nombreGym(String gymId) {
        return gymId == null ? "Tu gimnasio" : gymRepository.findById(gymId).map(Gym::getNombre).orElse("Tu gimnasio");
    }

    private static String vence(Pedido p) {
        Object v = p.getDatosPago() == null ? null : p.getDatosPago().get("vence");
        if (v == null) return "";
        return OffsetDateTime.parse(v.toString()).atZoneSameInstant(ZONA_MX).format(VENCE);
    }
}
