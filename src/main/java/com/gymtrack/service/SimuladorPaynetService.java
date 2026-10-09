package com.gymtrack.service;

import com.gymtrack.model.Pedido;
import com.gymtrack.repository.PedidoRepository;
import com.gymtrack.util.MetodosPago;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.OffsetDateTime;

// Lado de Spring del simulador de Paynet.
//
// En la vida real la tienda de conveniencia cobra y Paynet avisa. Aquí:
//  - "Simular pago en tienda" (dashboard de ventas o este endpoint) marca la
//    ficha como pagada: se activa el plan, sale el recibo por correo y el push
//    "Recibimos tu pago".
//  - Cada 15 minutos se cancelan las fichas que pasaron su fecha límite (72 h)
//    sin pagarse y se regresa el stock que tenían apartado.
@Service
public class SimuladorPaynetService {

    private static final Logger log = LoggerFactory.getLogger(SimuladorPaynetService.class);

    private final PedidoService pedidos;
    private final PedidoRepository pedidoRepository;

    public SimuladorPaynetService(PedidoService pedidos, PedidoRepository pedidoRepository) {
        this.pedidos = pedidos;
        this.pedidoRepository = pedidoRepository;
    }

    public Pedido simularPagoEnTienda(Pedido p) {
        if (!MetodosPago.PAYNET.equals(p.getProveedorPago())) {
            throw new TiendaException(HttpStatus.BAD_REQUEST, "Ese pedido no se pagó con Paynet.");
        }
        if (!Pedido.ESTADO_PENDIENTE_PAGO.equals(p.getEstado())) {
            throw new TiendaException(HttpStatus.CONFLICT, "La ficha ya no está pendiente (" + p.getEstado().replace('_', ' ') + ").");
        }
        if (vencida(p)) {
            throw new TiendaException(HttpStatus.CONFLICT, "La ficha ya venció: el pedido se cancelará.");
        }
        return pedidos.pagarFicha(p);
    }

    // Corre cada 15 minutos. También se puede forzar desde POST /api/simuladores/paynet/vencidas.
    @Scheduled(cron = "0 */15 * * * *")
    public int cancelarVencidas() {
        int canceladas = 0;
        for (Pedido p : pedidoRepository.findByEstadoAndProveedorPago(Pedido.ESTADO_PENDIENTE_PAGO, MetodosPago.PAYNET)) {
            if (!vencida(p)) continue;
            try {
                pedidos.cancelar(p);
                canceladas++;
            } catch (TiendaException e) {
                log.warn("No se pudo cancelar la ficha vencida del pedido #{}: {}", p.getFolio(), e.getMessage());
            }
        }
        if (canceladas > 0) log.info("Fichas Paynet vencidas canceladas: {}.", canceladas);
        return canceladas;
    }

    static boolean vencida(Pedido p) {
        Object vence = p.getDatosPago() == null ? null : p.getDatosPago().get("vence");
        if (vence == null) return false;
        try {
            return OffsetDateTime.parse(vence.toString()).toInstant().isBefore(Instant.now());
        } catch (Exception e) {
            return false;
        }
    }
}
