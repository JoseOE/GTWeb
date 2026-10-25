package com.gymtrack.service;

import com.gymtrack.model.Pedido;
import com.gymtrack.repository.PedidoRepository;
import com.gymtrack.util.MetodosPago;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

// Fichas de Paynet (sandbox): cuándo se pagan en la tienda. Openpay lo avisa
// por webhook, pero no se confía en el aviso: siempre se pregunta a Openpay el
// estado del cargo. Por si el aviso no llega, también se revisan solas cada dos
// minutos y al abrir el pedido (en la confirmación, Mis pedidos o Ventas).
//
// En el sandbox, el pago en tienda se simula desde el panel de Openpay
// (sandbox-dashboard.openpay.mx → Transacciones → la ficha).
@Service
public class FichasService {

    private static final Logger log = LoggerFactory.getLogger(FichasService.class);
    // Entre una revisión y otra del mismo pedido al abrirlo.
    private static final Duration ESPERA = Duration.ofSeconds(15);

    private final PagosService pagos;
    private final OpenpayApi openpay;
    private final PedidoService pedidos;
    private final PedidoRepository pedidoRepository;
    private final Map<String, Instant> revisadas = new ConcurrentHashMap<>();

    public FichasService(PagosService pagos, OpenpayApi openpay, PedidoService pedidos, PedidoRepository pedidoRepository) {
        this.pagos = pagos;
        this.openpay = openpay;
        this.pedidos = pedidos;
        this.pedidoRepository = pedidoRepository;
    }

    // Al abrir un pedido: si es una ficha de Openpay pendiente, se pregunta si ya
    // se pagó. Devuelve el pedido al día (o el mismo, si no cambió).
    public Pedido alDia(Pedido p) {
        if (!esFichaPendiente(p)) return p;
        Instant ultima = revisadas.get(p.getOrderId());
        if (ultima != null && ultima.plus(ESPERA).isAfter(Instant.now())) return p;
        return revisar(p);
    }

    public void alDia(String orderId) {
        if (pagos.sandbox() && orderId != null) pedidoRepository.findByOrderId(orderId).ifPresent(this::alDia);
    }

    // Aviso de Openpay (POST /api/webhooks/openpay). Solo dice qué cargo
    // cambió; el estado se pregunta a la API.
    @SuppressWarnings("unchecked")
    public void aviso(Map<String, Object> cuerpo) {
        Object tipo = cuerpo == null ? null : cuerpo.get("type");
        if ("verification".equals(tipo)) {
            // Al dar de alta el webhook, Openpay manda este código y pide escribirlo en su panel.
            log.info("Código de verificación del webhook de Openpay: {}", cuerpo.get("verification_code"));
            return;
        }
        Map<String, Object> transaccion = cuerpo != null && cuerpo.get("transaction") instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
        Object orderId = transaccion.get("order_id");
        if (orderId == null) return;
        pedidoRepository.findByOrderId(orderId.toString()).filter(this::esFichaPendiente).ifPresent(this::revisar);
    }

    @Scheduled(fixedDelay = 120_000, initialDelay = 60_000)
    public void revisarPendientes() {
        if (!pagos.sandbox() || !openpay.configurada()) return;
        for (Pedido p : pedidoRepository.findByEstadoAndProveedorPago(Pedido.ESTADO_PENDIENTE_PAGO, MetodosPago.PAYNET)) {
            if (esFichaPendiente(p)) revisar(p);
        }
    }

    private Pedido revisar(Pedido p) {
        revisadas.put(p.getOrderId(), Instant.now());
        Object cargoId = p.getDatosPago().get("cargoOpenpay");
        try {
            Map<String, Object> cargo = openpay.cargo(cargoId.toString());
            if ("completed".equals(cargo.get("status"))) {
                Object autorizacion = cargo.get("authorization");
                Pedido pagado = pedidos.pagarFicha(p, "paynet", "Tienda de la red Paynet",
                        autorizacion == null ? String.valueOf(cargoId) : autorizacion.toString());
                log.info("Openpay confirmó el pago de la ficha del pedido #{}.", p.getFolio());
                return pagado;
            }
        } catch (TiendaException e) {
            // La ficha ya no estaba pendiente (otro aviso ganó) u Openpay no respondió: se reintenta después.
            log.debug("No se actualizó la ficha del pedido #{}: {}", p.getFolio(), e.getMessage());
        }
        return pedidoRepository.findByOrderId(p.getOrderId()).orElse(p);
    }

    private boolean esFichaPendiente(Pedido p) {
        return pagos.sandbox() && p != null && MetodosPago.PAYNET.equals(p.getProveedorPago())
                && Pedido.ESTADO_PENDIENTE_PAGO.equals(p.getEstado())
                && p.getDatosPago() != null && p.getDatosPago().get("cargoOpenpay") != null;
    }
}
