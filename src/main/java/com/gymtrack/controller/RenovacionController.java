package com.gymtrack.controller;

import com.gymtrack.model.Pedido;
import com.gymtrack.model.Producto;
import com.gymtrack.model.User;
import com.gymtrack.service.AccesoService;
import com.gymtrack.service.CarritoService;
import com.gymtrack.service.ControlAccesoService;
import com.gymtrack.service.RenovacionAccesoService;
import com.gymtrack.service.TiendaException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// Pantalla "Renovar membresía" de la app: la abre el push que llega cuando el
// lector niega la entrada por membresía vencida (RenovacionAccesoService).
//
//   1. GET  → si está vencida, el plan sugerido, los planes para elegir y la
//             alerta de la puerta (si sigue esperando, si ya se abrió).
//   2. POST → deja el carrito de la app con ese plan solo, listo para pagar
//             con el checkout de siempre (tarjeta, PayPal o efectivo).
//   3. Al confirmarse el pago, la membresía se extiende y, si la alerta sigue
//      vigente, la puerta se abre sola (ControlAccesoService.abrirTrasRenovar).
@RestController
@RequestMapping("/api/tienda/renovacion")
public class RenovacionController {

    private final AccesoService acceso;
    private final CarritoService carritos;
    private final RenovacionAccesoService renovacion;
    private final ControlAccesoService control;

    public RenovacionController(AccesoService acceso, CarritoService carritos, RenovacionAccesoService renovacion,
                                ControlAccesoService control) {
        this.acceso = acceso;
        this.carritos = carritos;
        this.renovacion = renovacion;
        this.control = control;
    }

    // GET → {vencida, fechaProximoPago, planActual, plan, planes, puerta}
    //   puerta: {solicitudId, estado: PENDIENTE|APROBADA|RECHAZADA|EXPIRADA, venceEn, abiertaPorRenovacion, lector}
    //   o null si no hay una alerta suya en los últimos 10 minutos.
    @GetMapping
    public Map<String, Object> ver(@RequestHeader(value = AccesoService.ENCABEZADO, required = false) String userId) {
        User user = acceso.exigirComprador(userId);
        return estado(user);
    }

    // POST ?canal=app {"planId": "prod_..."} (opcional: sin él, el sugerido)
    //   → el estado de arriba más "carrito": el carrito ya con ese plan solo.
    @PostMapping
    public Map<String, Object> preparar(@RequestParam(required = false) String canal,
                                        @RequestHeader(value = AccesoService.ENCABEZADO, required = false) String userId,
                                        @RequestBody(required = false) Map<String, String> body) {
        User user = acceso.exigirComprador(userId);
        String elegido = canal == null || canal.isBlank() ? Pedido.CANAL_APP : canal.trim().toLowerCase();
        if (!CarritoService.CANALES.contains(elegido)) {
            throw new TiendaException(HttpStatus.BAD_REQUEST, "Canal no válido (web o app).");
        }
        String planId = body == null ? null : body.get("planId");
        List<Producto> planes = renovacion.planesRenovables(user.getGymId());
        Producto plan = planId == null || planId.isBlank()
                ? renovacion.planSugerido(user.getGymId(), user).orElse(null)
                : planes.stream().filter(p -> p.getId().equals(planId)).findFirst()
                        .orElseThrow(() -> new TiendaException(HttpStatus.NOT_FOUND, "Ese plan ya no está a la venta."));
        if (plan == null) {
            throw new TiendaException(HttpStatus.CONFLICT, "Tu gimnasio no tiene planes a la venta en la app. Renueva en recepción.");
        }
        // La renovación va sola: así el total es el del plan y nada más.
        carritos.vaciar(user, user.getGymId(), elegido);
        Map<String, Object> carrito = carritos.agregar(user, user.getGymId(), elegido, plan.getVariantes().get(0).getId(), 1);
        Map<String, Object> r = estado(user);
        r.put("plan", RenovacionAccesoService.vistaPlan(plan));
        r.put("carrito", carrito);
        return r;
    }

    private Map<String, Object> estado(User user) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("vencida", !ControlAccesoService.membresiaVigente(user));
        r.put("fechaProximoPago", user.getFechaProximoPago());
        r.put("planActual", user.getPlanActual());
        r.put("plan", renovacion.planSugerido(user.getGymId(), user).map(RenovacionAccesoService::vistaPlan).orElse(null));
        r.put("planes", renovacion.planesRenovables(user.getGymId()).stream().map(RenovacionAccesoService::vistaPlan).toList());
        r.put("puerta", control.alertaReciente(user.getId()).map(control::vistaAlerta).orElse(null));
        return r;
    }
}
