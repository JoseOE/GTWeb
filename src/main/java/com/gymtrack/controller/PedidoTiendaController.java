package com.gymtrack.controller;

import com.gymtrack.model.Pedido;
import com.gymtrack.repository.PedidoRepository;
import com.gymtrack.service.AccesoService;
import com.gymtrack.service.PedidoService;
import com.gymtrack.service.TiendaException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

// "Mis pedidos" y la página de confirmación. Cada quien ve solo los suyos;
// el dueño del gimnasio también puede abrir los de su tienda.
@RestController
@RequestMapping("/api/tienda/pedidos")
public class PedidoTiendaController {

    private final PedidoRepository pedidoRepository;
    private final PedidoService pedidoService;
    private final AccesoService acceso;

    public PedidoTiendaController(PedidoRepository pedidoRepository, PedidoService pedidoService, AccesoService acceso) {
        this.pedidoRepository = pedidoRepository;
        this.pedidoService = pedidoService;
        this.acceso = acceso;
    }

    @GetMapping
    public List<Map<String, Object>> misPedidos(@RequestHeader(value = AccesoService.ENCABEZADO, required = false) String userId) {
        String id = acceso.exigirUsuario(userId).getId();
        return pedidoRepository.findByUserIdOrderByCreadoEnDesc(id).stream().map(pedidoService::vista).toList();
    }

    // El pedido vive en MongoDB con su estado al día: no hay nada que volver a leer.
    @GetMapping("/{orderId}")
    public Map<String, Object> ver(@PathVariable String orderId,
                                   @RequestHeader(value = AccesoService.ENCABEZADO, required = false) String userId) {
        acceso.exigirUsuario(userId);
        Pedido pedido = pedidoRepository.findByOrderId(orderId)
                .orElseThrow(() -> new TiendaException(HttpStatus.NOT_FOUND, "Pedido no encontrado."));
        acceso.exigirAccesoAPedido(pedido, userId);
        return pedidoService.vista(pedido);
    }
}
