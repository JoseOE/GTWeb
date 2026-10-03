package com.gymtrack.controller;

import com.gymtrack.model.User;
import com.gymtrack.repository.UserRepository;
import com.gymtrack.service.AccesoService;
import com.gymtrack.service.CarritoService;
import com.gymtrack.service.PedidoService;
import com.gymtrack.service.TiendaException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// Venta en mostrador desde el panel. El ticket se arma en el navegador, así
// que agregar y quitar productos no espera al servidor (en el plan gratis de
// Render cada viaje a Medusa tarda segundos). Al cobrar llega completo: Spring
// lo revisa otra vez y crea el carrito en Medusa de una sola vez. El cliente se
// elige al cobrar: un miembro del gimnasio o el público en general.
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

    // POST /cobrar
    //   {"partidas": [{"varianteId": "variant_...", "cantidad": 2}, ...],
    //    "clienteId": null | "<userId de un miembro>", "metodo": "efectivo" | "tarjeta",
    //    "recibido": 500, "datos": {"token": "tok_sim_..."}, "totalVisto": 458}
    // → {"pedido": {...}, "cambio": 42.0}
    // Si algo del ticket cambió: 409 {"error": "...", "avisos": ["...", ...]}.
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
        CarritoService.VentaMostrador venta = carritos.cobrarMostrador(dueno, gymId, cliente, request.getPartidas(),
                request.getMetodo(), request.getRecibido(), request.getDatos(), request.getTotalVisto());
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
        private List<CarritoService.PartidaTicket> partidas;
        private String clienteId;
        private String metodo;
        private Double recibido;
        private Map<String, Object> datos;
        private Double totalVisto;

        public List<CarritoService.PartidaTicket> getPartidas() { return partidas; }
        public void setPartidas(List<CarritoService.PartidaTicket> partidas) { this.partidas = partidas; }

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
