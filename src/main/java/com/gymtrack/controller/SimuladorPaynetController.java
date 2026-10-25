package com.gymtrack.controller;

import com.gymtrack.model.Pedido;
import com.gymtrack.repository.PedidoRepository;
import com.gymtrack.service.AccesoService;
import com.gymtrack.service.PedidoService;
import com.gymtrack.service.SimuladorPaynetService;
import com.gymtrack.service.TiendaException;
import org.springframework.http.HttpStatus;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

// Endpoints de prueba del simulador de Paynet. Solo el dueño del gimnasio
// puede "pagar en tienda" las fichas de su tienda.
// Solo con PAGOS_MODO=simulado (las pruebas automáticas): en sandbox se paga
// con las APIs reales y esta ruta no existe.
@ConditionalOnProperty(name = "pagos.modo", havingValue = "simulado", matchIfMissing = true)
@RestController
@RequestMapping("/api/simuladores/paynet")
public class SimuladorPaynetController {

    private final SimuladorPaynetService paynet;
    private final PedidoService pedidoService;
    private final PedidoRepository pedidoRepository;
    private final AccesoService acceso;

    public SimuladorPaynetController(SimuladorPaynetService paynet, PedidoService pedidoService,
                                     PedidoRepository pedidoRepository, AccesoService acceso) {
        this.paynet = paynet;
        this.pedidoService = pedidoService;
        this.pedidoRepository = pedidoRepository;
        this.acceso = acceso;
    }

    // POST /api/simuladores/paynet/{orderId}/pagar → "Simular pago en tienda".
    // Lo usa el dashboard de ventas; responde el pedido ya pagado.
    // Cuerpo opcional: {"tienda": "oxxo" | "7-eleven" | "walmart" | ...}; sin él, una al azar.
    @PostMapping("/{orderId}/pagar")
    public Map<String, Object> pagar(@PathVariable String orderId,
                                     @RequestBody(required = false) Map<String, String> cuerpo,
                                     @RequestHeader(value = AccesoService.ENCABEZADO, required = false) String userId) {
        Pedido p = pedidoRepository.findByOrderId(orderId)
                .orElseThrow(() -> new TiendaException(HttpStatus.NOT_FOUND, "Pedido no encontrado."));
        acceso.exigirDueno(p.getGymId(), userId);
        return pedidoService.vista(paynet.simularPagoEnTienda(p, cuerpo == null ? null : cuerpo.get("tienda")));
    }

    // POST /api/simuladores/paynet/vencidas → cancela ahora las fichas vencidas
    // (el revisor ya corre solo cada 15 minutos; esto es para probarlo).
    @PostMapping("/vencidas")
    public Map<String, Object> vencidas(@RequestHeader(value = AccesoService.ENCABEZADO, required = false) String userId) {
        if (!"owner".equals(acceso.exigirUsuario(userId).getRole())) {
            throw new TiendaException(HttpStatus.FORBIDDEN, "Solo los dueños de gimnasio pueden hacer eso.");
        }
        return Map.of("canceladas", paynet.cancelarVencidas());
    }
}
