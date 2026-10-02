package com.gymtrack.controller;

import com.gymtrack.model.Pedido;
import com.gymtrack.model.User;
import com.gymtrack.repository.UserRepository;
import com.gymtrack.service.AccesoService;
import com.gymtrack.service.CarritoService;
import com.gymtrack.service.PedidoService;
import com.gymtrack.service.TiendaException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

// Venta en mostrador desde el panel. El carrito es del dueño (canal
// "mostrador") y se guarda igual que el de los miembros, así que sobrevive a
// recargar la página. El cliente se elige al cobrar: un miembro del gimnasio o
// el público en general.
@RestController
@RequestMapping("/api/gyms/{gymId}/mostrador")
public class MostradorController {

    private final CarritoService carritos;
    private final PedidoService pedidos;
    private final AccesoService acceso;
    private final UserRepository userRepository;

    public MostradorController(CarritoService carritos, PedidoService pedidos, AccesoService acceso,
                               UserRepository userRepository) {
        this.carritos = carritos;
        this.pedidos = pedidos;
        this.acceso = acceso;
        this.userRepository = userRepository;
    }

    @GetMapping("/carrito")
    public Map<String, Object> ver(@PathVariable String gymId,
                                   @RequestHeader(value = AccesoService.ENCABEZADO, required = false) String userId) {
        return carritos.ver(dueno(gymId, userId), gymId, Pedido.CANAL_MOSTRADOR);
    }

    // DELETE /carrito → "Nueva venta": descarta lo que había.
    @DeleteMapping("/carrito")
    public Map<String, Object> vaciar(@PathVariable String gymId,
                                      @RequestHeader(value = AccesoService.ENCABEZADO, required = false) String userId) {
        return carritos.vaciar(dueno(gymId, userId), gymId, Pedido.CANAL_MOSTRADOR);
    }

    @PostMapping("/carrito/items")
    public Map<String, Object> agregar(@PathVariable String gymId,
                                       @RequestHeader(value = AccesoService.ENCABEZADO, required = false) String userId,
                                       @RequestBody CarritoController.PartidaRequest request) {
        return carritos.agregar(dueno(gymId, userId), gymId, Pedido.CANAL_MOSTRADOR, request.getVarianteId(), request.getCantidad());
    }

    @PatchMapping("/carrito/items/{partidaId}")
    public Map<String, Object> cambiar(@PathVariable String gymId, @PathVariable String partidaId,
                                       @RequestHeader(value = AccesoService.ENCABEZADO, required = false) String userId,
                                       @RequestBody CarritoController.PartidaRequest request) {
        return carritos.cambiarCantidad(dueno(gymId, userId), gymId, Pedido.CANAL_MOSTRADOR, partidaId, request.getCantidad());
    }

    @DeleteMapping("/carrito/items/{partidaId}")
    public Map<String, Object> quitar(@PathVariable String gymId, @PathVariable String partidaId,
                                      @RequestHeader(value = AccesoService.ENCABEZADO, required = false) String userId) {
        return carritos.quitar(dueno(gymId, userId), gymId, Pedido.CANAL_MOSTRADOR, partidaId);
    }

    // POST /cobrar
    //   {"clienteId": null | "<userId de un miembro>", "metodo": "efectivo" | "tarjeta",
    //    "recibido": 500, "datos": {"token": "tok_sim_..."}, "totalVisto": 458}
    // → {"pedido": {...}, "cambio": 42.0}
    @PostMapping("/cobrar")
    public Map<String, Object> cobrar(@PathVariable String gymId,
                                      @RequestHeader(value = AccesoService.ENCABEZADO, required = false) String userId,
                                      @RequestBody CobroRequest request) {
        User dueno = dueno(gymId, userId);
        User cliente = null;
        if (request.getClienteId() != null && !request.getClienteId().isBlank()) {
            // Cualquier miembro vinculado a este gimnasio, aun con la solicitud
            // pendiente: en recepción el dueño decide a quién le cobra.
            cliente = userRepository.findById(request.getClienteId())
                    .filter(u -> "member".equals(u.getRole()) && gymId.equals(u.getGymId()))
                    .orElseThrow(() -> new TiendaException(HttpStatus.BAD_REQUEST, "Ese cliente no es miembro de tu gimnasio."));
        }
        CarritoService.VentaMostrador venta = carritos.cobrarMostrador(dueno, gymId, cliente, request.getMetodo(),
                request.getRecibido(), request.getDatos(), request.getTotalVisto());
        Map<String, Object> respuesta = new LinkedHashMap<>();
        respuesta.put("pedido", pedidos.vista(venta.pedido()));
        respuesta.put("cambio", venta.cambio());
        if (cliente != null) {
            // Para avisar en pantalla hasta cuándo quedó su membresía si llevaba un plan.
            User actualizado = userRepository.findById(cliente.getId()).orElse(cliente);
            respuesta.put("cliente", Map.of(
                    "id", actualizado.getId(),
                    "nombre", actualizado.getNombre() == null ? "" : actualizado.getNombre(),
                    "fechaProximoPago", actualizado.getFechaProximoPago() == null ? "" : actualizado.getFechaProximoPago().toString()));
        }
        return respuesta;
    }

    private User dueno(String gymId, String userId) {
        acceso.exigirDueno(gymId, userId);
        return acceso.exigirUsuario(userId);
    }

    public static class CobroRequest {
        private String clienteId;
        private String metodo;
        private Double recibido;
        private Map<String, Object> datos;
        private Double totalVisto;

        public String getClienteId() { return clienteId; }
        public void setClienteId(String clienteId) { this.clienteId = clienteId; }

        public String getMetodo() { return metodo; }
        public void setMetodo(String metodo) { this.metodo = metodo; }

        public Double getRecibido() { return recibido; }
        public void setRecibido(Double recibido) { this.recibido = recibido; }

        public Map<String, Object> getDatos() { return datos; }
        public void setDatos(Map<String, Object> datos) { this.datos = datos; }

        public Double getTotalVisto() { return totalVisto; }
        public void setTotalVisto(Double totalVisto) { this.totalVisto = totalVisto; }
    }
}
