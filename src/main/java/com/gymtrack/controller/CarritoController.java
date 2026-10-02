package com.gymtrack.controller;

import com.gymtrack.model.Pedido;
import com.gymtrack.model.User;
import com.gymtrack.service.AccesoService;
import com.gymtrack.service.CarritoService;
import com.gymtrack.service.PedidoService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

// Carrito del miembro en la tienda de su gimnasio. Es el mismo en cualquier
// dispositivo donde inicie sesión: se identifica por usuario, gimnasio y canal
// (?canal=web por defecto; la app manda ?canal=app).
@RestController
@RequestMapping("/api/tienda/carrito")
public class CarritoController {

    private final CarritoService carritos;
    private final PedidoService pedidos;
    private final AccesoService acceso;

    public CarritoController(CarritoService carritos, PedidoService pedidos, AccesoService acceso) {
        this.carritos = carritos;
        this.pedidos = pedidos;
        this.acceso = acceso;
    }

    // GET → el carrito revisado: si algo se agotó, se ocultó o cambió de precio,
    // ya viene corregido y "avisos" dice qué cambió.
    @GetMapping
    public Map<String, Object> ver(@RequestParam(required = false) String canal,
                                   @RequestHeader(value = AccesoService.ENCABEZADO, required = false) String userId) {
        return carritos.ver(acceso.exigirComprador(userId), canal);
    }

    // POST → abre el carrito en Medusa si todavía no existe (no es obligatorio:
    // agregar el primer producto también lo abre).
    @PostMapping
    public Map<String, Object> asegurar(@RequestParam(required = false) String canal,
                                        @RequestHeader(value = AccesoService.ENCABEZADO, required = false) String userId) {
        return carritos.asegurar(acceso.exigirComprador(userId), canal);
    }

    // DELETE → vacía el carrito.
    @DeleteMapping
    public Map<String, Object> vaciar(@RequestParam(required = false) String canal,
                                      @RequestHeader(value = AccesoService.ENCABEZADO, required = false) String userId) {
        return carritos.vaciar(acceso.exigirComprador(userId), canal);
    }

    // POST /items  {"varianteId": "variant_...", "cantidad": 2}
    @PostMapping("/items")
    public Map<String, Object> agregar(@RequestParam(required = false) String canal,
                                       @RequestHeader(value = AccesoService.ENCABEZADO, required = false) String userId,
                                       @RequestBody PartidaRequest request) {
        return carritos.agregar(acceso.exigirComprador(userId), canal, request.getVarianteId(), request.getCantidad());
    }

    // PATCH /items/{id}  {"cantidad": 3}  (0 la quita)
    @PatchMapping("/items/{partidaId}")
    public Map<String, Object> cambiar(@PathVariable String partidaId,
                                       @RequestParam(required = false) String canal,
                                       @RequestHeader(value = AccesoService.ENCABEZADO, required = false) String userId,
                                       @RequestBody PartidaRequest request) {
        return carritos.cambiarCantidad(acceso.exigirComprador(userId), canal, partidaId, request.getCantidad());
    }

    @DeleteMapping("/items/{partidaId}")
    public Map<String, Object> quitar(@PathVariable String partidaId,
                                      @RequestParam(required = false) String canal,
                                      @RequestHeader(value = AccesoService.ENCABEZADO, required = false) String userId) {
        return carritos.quitar(acceso.exigirComprador(userId), canal, partidaId);
    }

    // POST /checkout  {"metodo": "stripe" | "paynet" | "paypal", "datos": {...}, "totalVisto": 934.5}
    //  - 200 → el pedido (el mismo formato de GET /api/tienda/pedidos/{id});
    //  - 409 → el carrito cambió: trae "avisos" y "carrito" ya corregido;
    //  - 402 → el simulador rechazó el pago, con su mensaje.
    @PostMapping("/checkout")
    public Map<String, Object> checkout(@RequestParam(required = false) String canal,
                                        @RequestHeader(value = AccesoService.ENCABEZADO, required = false) String userId,
                                        @RequestBody CheckoutRequest request) {
        User user = acceso.exigirComprador(userId);
        Pedido pedido = carritos.checkout(user, canal, request.getMetodo(), request.getDatos(), request.getTotalVisto());
        return pedidos.vista(pedido);
    }

    public static class PartidaRequest {
        private String varianteId;
        private Integer cantidad;

        public String getVarianteId() { return varianteId; }
        public void setVarianteId(String varianteId) { this.varianteId = varianteId; }

        public Integer getCantidad() { return cantidad; }
        public void setCantidad(Integer cantidad) { this.cantidad = cantidad; }
    }

    public static class CheckoutRequest {
        private String metodo;
        // Lo que armó el módulo JS del método (token de prueba, cuenta PayPal...).
        private Map<String, Object> datos;
        private Double totalVisto;

        public String getMetodo() { return metodo; }
        public void setMetodo(String metodo) { this.metodo = metodo; }

        public Map<String, Object> getDatos() { return datos; }
        public void setDatos(Map<String, Object> datos) { this.datos = datos; }

        public Double getTotalVisto() { return totalVisto; }
        public void setTotalVisto(Double totalVisto) { this.totalVisto = totalVisto; }
    }
}
