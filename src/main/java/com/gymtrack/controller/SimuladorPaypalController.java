package com.gymtrack.controller;

import com.gymtrack.model.Pedido;
import com.gymtrack.model.User;
import com.gymtrack.service.AccesoService;
import com.gymtrack.service.CarritoService;
import com.gymtrack.service.SimuladorPaypalService;
import com.gymtrack.service.TiendaException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

// Simulador de la API de órdenes de PayPal.
//
// Crear la orden exige la sesión del comprador. Verla, aprobarla y cancelarla
// no: paypal-sim.html se puede abrir desde el navegador de la app, que no tiene
// la sesión de la página, y el id de la orden hace de llave (como el token de
// la URL de PayPal). Pagar con ella sí vuelve a exigir al comprador.
// La cuenta vinculada (ver y desvincular) es de quien tiene la sesión.
// Solo con PAGOS_MODO=simulado (las pruebas automáticas): en sandbox se paga
// con las APIs reales y esta ruta no existe.
@ConditionalOnProperty(name = "pagos.modo", havingValue = "simulado", matchIfMissing = true)
@RestController
@RequestMapping("/api/simuladores/paypal")
public class SimuladorPaypalController {

    private final SimuladorPaypalService paypal;
    private final CarritoService carritos;
    private final AccesoService acceso;

    public SimuladorPaypalController(SimuladorPaypalService paypal, CarritoService carritos, AccesoService acceso) {
        this.paypal = paypal;
        this.carritos = carritos;
        this.acceso = acceso;
    }

    // POST /api/simuladores/paypal/ordenes?canal=web|app
    //   {"returnUrl": "https://.../checkout.html?metodo=paypal&continuar=1" | "gymtrack://pago-paypal",
    //    "cancelUrl": "https://.../checkout.html?metodo=paypal&paypal=cancelado",
    //    "recordar": true}   ← opcional: al pagar, la cuenta queda vinculada
    // → {"id": "PAYID-SIM-...", "monto": 458.0, "moneda": "MXN", "aprobarUrl": "https://.../paypal-sim.html?token=..."}
    @PostMapping("/ordenes")
    public Map<String, Object> crear(@RequestParam(required = false) String canal,
                                     @RequestHeader(value = AccesoService.ENCABEZADO, required = false) String userId,
                                     @RequestBody OrdenRequest request,
                                     HttpServletRequest http) {
        User comprador = acceso.exigirComprador(userId);
        String elegido = canal == null || canal.isBlank() ? Pedido.CANAL_WEB : canal.trim().toLowerCase();
        if (!CarritoService.CANALES.contains(elegido)) {
            throw new TiendaException(HttpStatus.BAD_REQUEST, "Canal no válido (web o app).");
        }
        Map<String, Object> carrito = carritos.ver(comprador, comprador.getGymId(), elegido);
        if (!((List<?>) carrito.get("avisos")).isEmpty()) {
            // Algo del carrito cambió (precio, existencias): que lo vea antes de aprobar.
            throw new TiendaException(HttpStatus.CONFLICT, "Tu carrito cambió. Revísalo antes de pagar.",
                    Map.of("avisos", carrito.get("avisos"), "carrito", carrito));
        }
        return paypal.crear(comprador, elegido, ((Number) carrito.get("total")).doubleValue(),
                ((Number) carrito.get("articulos")).intValue(), request.getReturnUrl(), request.getCancelUrl(), http.getServerName(),
                Boolean.TRUE.equals(request.getRecordar()));
    }

    @GetMapping("/ordenes/{id}")
    public Map<String, Object> ver(@PathVariable String id) {
        return paypal.ver(id);
    }

    // {"cuenta": "ana.compradora@sim-paypal.test"} → {"estado": "aprobada", "redirect": "<returnUrl>?token=...&PayerID=..."}
    @PostMapping("/ordenes/{id}/aprobar")
    public Map<String, Object> aprobar(@PathVariable String id, @RequestBody AprobarRequest request) {
        return paypal.aprobar(id, request.getCuenta());
    }

    // → {"estado": "cancelada", "redirect": "<cancelUrl>?token=..."}
    @PostMapping("/ordenes/{id}/cancelar")
    public Map<String, Object> cancelar(@PathVariable String id) {
        return paypal.cancelar(id);
    }

    // GET /api/simuladores/paypal/vinculo → {"vinculada": true, "cuenta", "nombre", "desde"}
    @GetMapping("/vinculo")
    public Map<String, Object> vinculo(@RequestHeader(value = AccesoService.ENCABEZADO, required = false) String userId) {
        return paypal.vinculo(acceso.exigirUsuario(userId).getId());
    }

    // DELETE /api/simuladores/paypal/vinculo → {"vinculada": false}
    @DeleteMapping("/vinculo")
    public Map<String, Object> desvincular(@RequestHeader(value = AccesoService.ENCABEZADO, required = false) String userId) {
        return paypal.desvincular(acceso.exigirUsuario(userId).getId());
    }

    public static class OrdenRequest {
        private String returnUrl;
        private String cancelUrl;
        private Boolean recordar;

        public Boolean getRecordar() { return recordar; }
        public void setRecordar(Boolean recordar) { this.recordar = recordar; }

        public String getReturnUrl() { return returnUrl; }
        public void setReturnUrl(String returnUrl) { this.returnUrl = returnUrl; }

        public String getCancelUrl() { return cancelUrl; }
        public void setCancelUrl(String cancelUrl) { this.cancelUrl = cancelUrl; }
    }

    public static class AprobarRequest {
        private String cuenta;

        public String getCuenta() { return cuenta; }
        public void setCuenta(String cuenta) { this.cuenta = cuenta; }
    }
}
