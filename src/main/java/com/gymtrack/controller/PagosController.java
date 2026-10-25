package com.gymtrack.controller;

import com.gymtrack.model.Pedido;
import com.gymtrack.model.User;
import com.gymtrack.service.AccesoService;
import com.gymtrack.service.CarritoService;
import com.gymtrack.service.PagosService;
import com.gymtrack.service.TiendaException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

// Antes de cobrar el carrito: abre la página de pago de Stripe o la orden de
// PayPal por el total de hoy. La persona paga (o aprueba) ahí, regresa a la
// tienda o a la app, y POST /api/tienda/carrito/checkout cobra con lo que
// devolvió el proveedor ({sesion} de Stripe, {token, payerId} de PayPal).
@RestController
public class PagosController {

    private final PagosService pagos;
    private final CarritoService carritos;
    private final AccesoService acceso;

    public PagosController(PagosService pagos, CarritoService carritos, AccesoService acceso) {
        this.pagos = pagos;
        this.carritos = carritos;
        this.acceso = acceso;
    }

    // POST /api/pagos/stripe/sesiones?canal=web|app {returnUrl, cancelUrl} → {id, url, monto}
    // Al pagar, Stripe vuelve a returnUrl con ?session_id=cs_...
    @PostMapping("/api/pagos/stripe/sesiones")
    @SuppressWarnings("unchecked")
    public Map<String, Object> sesionStripe(@RequestParam(required = false) String canal,
                                            @RequestHeader(value = AccesoService.ENCABEZADO, required = false) String userId,
                                            @RequestBody Regreso request, HttpServletRequest http) {
        User comprador = acceso.exigirComprador(userId);
        String elegido = canal(canal);
        Map<String, Object> carrito = carritoSinCambios(comprador, elegido);
        List<PagosService.Concepto> conceptos = new ArrayList<>();
        for (Map<String, Object> i : (List<Map<String, Object>>) carrito.get("items")) {
            String variante = i.get("variante") == null ? "" : i.get("variante").toString();
            String nombre = i.get("titulo") + (variante.isBlank() || "Única".equals(variante) ? "" : " · " + variante);
            conceptos.add(new PagosService.Concepto(nombre, ((Number) i.get("cantidad")).intValue(), ((Number) i.get("precioUnitario")).doubleValue()));
        }
        return pagos.crearSesionStripe(comprador, comprador.getGymId(), elegido, ((Number) carrito.get("total")).doubleValue(),
                conceptos, request.getReturnUrl(), request.getCancelUrl(), http.getServerName());
    }

    // POST /api/pagos/paypal/ordenes?canal=web|app {returnUrl, cancelUrl} → {id, monto, moneda, aprobarUrl}
    // Al aprobar, PayPal vuelve a returnUrl con ?token=<id de la orden>&PayerID=...
    @PostMapping("/api/pagos/paypal/ordenes")
    public Map<String, Object> ordenPaypal(@RequestParam(required = false) String canal,
                                           @RequestHeader(value = AccesoService.ENCABEZADO, required = false) String userId,
                                           @RequestBody Regreso request, HttpServletRequest http) {
        User comprador = acceso.exigirComprador(userId);
        String elegido = canal(canal);
        Map<String, Object> carrito = carritoSinCambios(comprador, elegido);
        return pagos.crearOrdenPaypal(comprador, elegido, ((Number) carrito.get("total")).doubleValue(),
                ((Number) carrito.get("articulos")).intValue(), request.getReturnUrl(), request.getCancelUrl(), http.getServerName());
    }

    // GET /api/pagos/modo → {modo: "sandbox" | "simulado"}: la página y la app
    // dicen "modo de prueba" y qué cuentas de prueba usar.
    @GetMapping("/api/pagos/modo")
    public Map<String, Object> modo() {
        return Map.of("modo", pagos.sandbox() ? PagosService.SANDBOX : "simulado");
    }

    private Map<String, Object> carritoSinCambios(User comprador, String canal) {
        Map<String, Object> carrito = carritos.ver(comprador, comprador.getGymId(), canal);
        if (!((List<?>) carrito.get("avisos")).isEmpty()) {
            throw new TiendaException(HttpStatus.CONFLICT, "Tu carrito cambió. Revísalo antes de pagar.",
                    Map.of("avisos", carrito.get("avisos"), "carrito", carrito));
        }
        if (((List<?>) carrito.get("items")).isEmpty()) {
            throw new TiendaException(HttpStatus.BAD_REQUEST, "Tu carrito está vacío.");
        }
        return carrito;
    }

    private static String canal(String canal) {
        String elegido = canal == null || canal.isBlank() ? Pedido.CANAL_WEB : canal.trim().toLowerCase();
        if (!CarritoService.CANALES.contains(elegido)) {
            throw new TiendaException(HttpStatus.BAD_REQUEST, "Canal no válido (web o app).");
        }
        return elegido;
    }

    public static class Regreso {
        private String returnUrl;
        private String cancelUrl;

        public String getReturnUrl() { return returnUrl; }
        public void setReturnUrl(String returnUrl) { this.returnUrl = returnUrl; }

        public String getCancelUrl() { return cancelUrl; }
        public void setCancelUrl(String cancelUrl) { this.cancelUrl = cancelUrl; }
    }
}
