package com.gymtrack.controller;

import com.gymtrack.model.User;
import com.gymtrack.service.AccesoService;
import com.gymtrack.service.OpinionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

// Opiniones sobre GymTrack (ver OpinionService).
@RestController
@RequestMapping("/api/opiniones")
public class OpinionController {

    private final OpinionService opiniones;
    private final AccesoService acceso;

    public OpinionController(OpinionService opiniones, AccesoService acceso) {
        this.opiniones = opiniones;
        this.acceso = acceso;
    }

    // GET /destacadas?limite=12 (pública) → [{autor, rol, estrellas, texto, fecha,
    // gimnasio: {nombre, foto, logo, colorPrimario}}], solo las de 4★ o más.
    @GetMapping("/destacadas")
    public List<Map<String, Object>> destacadas(@RequestParam(defaultValue = "12") int limite) {
        return opiniones.destacadas(limite);
    }

    // GET /mia → la opinión de quien llama, o {} si todavía no opina.
    @GetMapping("/mia")
    public Map<String, Object> mia(@RequestHeader(value = AccesoService.ENCABEZADO, required = false) String userId) {
        User user = acceso.exigirUsuario(userId);
        return opiniones.mia(user).orElse(Map.of());
    }

    // PUT /mia {"estrellas": 5, "texto": "..."} → la opinión guardada.
    @PutMapping("/mia")
    public Map<String, Object> guardar(@RequestHeader(value = AccesoService.ENCABEZADO, required = false) String userId,
                                       @RequestBody Map<String, Object> cuerpo) {
        User user = acceso.exigirUsuario(userId);
        Object estrellas = cuerpo.get("estrellas");
        Integer valor = estrellas instanceof Number n && n.doubleValue() == Math.rint(n.doubleValue()) ? n.intValue() : null;
        return opiniones.guardar(user, valor, cuerpo.get("texto") == null ? null : cuerpo.get("texto").toString());
    }

    @DeleteMapping("/mia")
    public ResponseEntity<?> borrar(@RequestHeader(value = AccesoService.ENCABEZADO, required = false) String userId) {
        opiniones.borrar(acceso.exigirUsuario(userId));
        return ResponseEntity.ok(Map.of("message", "Tu opinión se borró."));
    }
}
