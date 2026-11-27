package com.gymtrack.controller;

import com.gymtrack.model.Gym;
import com.gymtrack.model.PreciosSuscripcion;
import com.gymtrack.model.User;
import com.gymtrack.service.AccesoService;
import com.gymtrack.service.AdministradorService;
import com.gymtrack.service.SuscripcionService;
import com.gymtrack.service.TiendaException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.Map;

// La suscripción de cada gimnasio a GymTrack.
//   Dueño (su panel, pestaña Suscripción):
//     GET  /api/gyms/{gymId}/suscripcion                 estado, precios, cotizaciones y pagos
//     POST /api/gyms/{gymId}/suscripcion/stripe/sesion   {plan, returnUrl, cancelUrl} → {id, url}
//     POST /api/gyms/{gymId}/suscripcion/paypal/orden    {plan, returnUrl, cancelUrl} → {id, aprobarUrl}
//     POST /api/gyms/{gymId}/suscripcion/pagar           {plan, metodo: tarjeta|paypal, datos}
//   Equipo de GymTrack (admin.html, pestaña Suscripciones):
//     GET  /api/admin/suscripciones                      todos los gimnasios, cobrado y pagos
//     PUT  /api/admin/suscripciones/precios              {anual, mensual, diasGracia}
//     POST /api/admin/suscripciones/{gymId}/cortesia     {hasta: "2027-01-31"}
@RestController
public class SuscripcionController {

    private final SuscripcionService suscripciones;
    private final AccesoService acceso;
    private final AdministradorService admin;

    public SuscripcionController(SuscripcionService suscripciones, AccesoService acceso, AdministradorService admin) {
        this.suscripciones = suscripciones;
        this.acceso = acceso;
        this.admin = admin;
    }

    @GetMapping("/api/gyms/{gymId}/suscripcion")
    public Map<String, Object> ver(@PathVariable String gymId,
                                   @RequestHeader(value = AccesoService.ENCABEZADO, required = false) String userId) {
        return suscripciones.vistaDueno(acceso.exigirDueno(gymId, userId));
    }

    @PostMapping("/api/gyms/{gymId}/suscripcion/stripe/sesion")
    public Map<String, Object> sesionTarjeta(@PathVariable String gymId,
                                             @RequestHeader(value = AccesoService.ENCABEZADO, required = false) String userId,
                                             @RequestBody Map<String, String> body, HttpServletRequest http) {
        Gym g = acceso.exigirDueno(gymId, userId);
        return suscripciones.sesionTarjeta(acceso.exigirUsuario(userId), g, body.get("plan"),
                body.get("returnUrl"), body.get("cancelUrl"), http.getServerName());
    }

    @PostMapping("/api/gyms/{gymId}/suscripcion/paypal/orden")
    public Map<String, Object> ordenPaypal(@PathVariable String gymId,
                                           @RequestHeader(value = AccesoService.ENCABEZADO, required = false) String userId,
                                           @RequestBody Map<String, String> body, HttpServletRequest http) {
        Gym g = acceso.exigirDueno(gymId, userId);
        return suscripciones.ordenPaypal(acceso.exigirUsuario(userId), g, body.get("plan"),
                body.get("returnUrl"), body.get("cancelUrl"), http.getServerName());
    }

    @PostMapping("/api/gyms/{gymId}/suscripcion/pagar")
    @SuppressWarnings("unchecked")
    public Map<String, Object> pagar(@PathVariable String gymId,
                                     @RequestHeader(value = AccesoService.ENCABEZADO, required = false) String userId,
                                     @RequestBody Map<String, Object> body) {
        Gym g = acceso.exigirDueno(gymId, userId);
        Map<String, Object> datos = body.get("datos") instanceof Map<?, ?> m ? new LinkedHashMap<>((Map<String, Object>) m) : new LinkedHashMap<>();
        return suscripciones.pagar(acceso.exigirUsuario(userId), g, texto(body.get("plan")), texto(body.get("metodo")), datos);
    }

    // ─── Equipo de GymTrack ───

    @GetMapping("/api/admin/suscripciones")
    public Map<String, Object> todas(@RequestHeader(value = AccesoService.ENCABEZADO, required = false) String userId) {
        exigirEquipo(userId);
        return suscripciones.vistaEquipo();
    }

    @PutMapping("/api/admin/suscripciones/precios")
    public Map<String, Object> precios(@RequestHeader(value = AccesoService.ENCABEZADO, required = false) String userId,
                                       @RequestBody Map<String, Object> body) {
        exigirEquipo(userId);
        PreciosSuscripcion p = suscripciones.cambiarPrecios(numero(body.get("anual")), numero(body.get("mensual")),
                body.get("diasGracia") == null ? null : numero(body.get("diasGracia")).intValue());
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("anual", p.getAnual());
        v.put("mensual", p.getMensual());
        v.put("diasGracia", p.getDiasGracia());
        return v;
    }

    @PostMapping("/api/admin/suscripciones/{gymId}/cortesia")
    public Map<String, Object> cortesia(@PathVariable String gymId,
                                        @RequestHeader(value = AccesoService.ENCABEZADO, required = false) String userId,
                                        @RequestBody Map<String, Object> body) {
        exigirEquipo(userId);
        LocalDate hasta;
        try {
            hasta = LocalDate.parse(texto(body.get("hasta")));
        } catch (DateTimeParseException e) {
            throw new TiendaException(HttpStatus.BAD_REQUEST, "Elige hasta qué fecha va la cortesía.");
        }
        return suscripciones.darCortesia(gymId, hasta);
    }

    private User exigirEquipo(String userId) {
        return admin.exigirAdmin(acceso.exigirUsuario(userId));
    }

    private static String texto(Object o) {
        return o == null ? "" : o.toString().trim();
    }

    private static Double numero(Object o) {
        if (o instanceof Number n) return n.doubleValue();
        try {
            return o == null ? null : Double.parseDouble(o.toString().replace(",", "").replace("$", "").trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
