package com.gymtrack.controller;

import com.gymtrack.model.Gym;
import com.gymtrack.service.AccesoService;
import com.gymtrack.service.CatalogoService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

// Planes de membresía que el gimnasio vende (visita, semana, mensual,
// trimestral...) y la inscripción de pago único. Son productos de tipo
// "membresia": al pagarse extienden la membresía del miembro.
@RestController
@RequestMapping("/api/gyms/{gymId}/planes")
public class PlanController {

    private final CatalogoService catalogo;
    private final AccesoService acceso;

    public PlanController(CatalogoService catalogo, AccesoService acceso) {
        this.catalogo = catalogo;
        this.acceso = acceso;
    }

    @GetMapping
    public List<Map<String, Object>> listar(@PathVariable String gymId,
                                            @RequestHeader(value = AccesoService.ENCABEZADO, required = false) String userId) {
        acceso.exigirDueno(gymId, userId);
        return catalogo.listarPlanes(gymId);
    }

    // GET /api/gyms/{gymId}/planes/presets → planes sugeridos con el precio
    // calculado desde la cuota mensual. No guarda nada: el panel los prellena.
    @GetMapping("/presets")
    public List<Map<String, Object>> presets(@PathVariable String gymId,
                                             @RequestHeader(value = AccesoService.ENCABEZADO, required = false) String userId) {
        Gym gym = acceso.exigirDueno(gymId, userId);
        return catalogo.presets(gym);
    }

    @PostMapping
    public Map<String, Object> crear(@PathVariable String gymId,
                                     @RequestHeader(value = AccesoService.ENCABEZADO, required = false) String userId,
                                     @RequestBody CatalogoService.PlanRequest request) {
        acceso.exigirDueno(gymId, userId);
        return catalogo.crearPlan(gymId, request);
    }

    @PutMapping("/{planId}")
    public Map<String, Object> actualizar(@PathVariable String gymId, @PathVariable String planId,
                                          @RequestHeader(value = AccesoService.ENCABEZADO, required = false) String userId,
                                          @RequestBody CatalogoService.PlanRequest request) {
        acceso.exigirDueno(gymId, userId);
        return catalogo.actualizarPlan(gymId, planId, request);
    }

    @DeleteMapping("/{planId}")
    public ResponseEntity<?> eliminar(@PathVariable String gymId, @PathVariable String planId,
                                      @RequestHeader(value = AccesoService.ENCABEZADO, required = false) String userId) {
        acceso.exigirDueno(gymId, userId);
        catalogo.eliminarPlan(gymId, planId);
        return ResponseEntity.ok(Map.of("message", "Plan eliminado."));
    }
}
