package com.gymtrack.controller;

import com.gymtrack.service.AccesoService;
import com.gymtrack.service.AnuncioService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

// Anuncios del gimnasio (ver AnuncioService).
//  - Panel: el dueño los crea, edita, pausa y borra.
//  - App: el miembro ve los vigentes de su gimnasio.
@RestController
public class AnuncioController {

    private final AnuncioService anuncios;
    private final AccesoService acceso;

    public AnuncioController(AnuncioService anuncios, AccesoService acceso) {
        this.anuncios = anuncios;
        this.acceso = acceso;
    }

    @GetMapping("/api/gyms/{gymId}/anuncios")
    public List<Map<String, Object>> listar(@PathVariable String gymId,
                                            @RequestHeader(value = AccesoService.ENCABEZADO, required = false) String userId) {
        acceso.exigirDueno(gymId, userId);
        return anuncios.delGimnasio(gymId);
    }

    // {"titulo", "texto", "tipo": "aviso|promocion|cierre", "desde": "2026-11-05", "hasta": null, "avisar": true}
    @PostMapping("/api/gyms/{gymId}/anuncios")
    public Map<String, Object> crear(@PathVariable String gymId, @RequestBody AnuncioService.Solicitud solicitud,
                                     @RequestHeader(value = AccesoService.ENCABEZADO, required = false) String userId) {
        acceso.exigirDueno(gymId, userId);
        return anuncios.guardar(gymId, null, solicitud);
    }

    @PutMapping("/api/gyms/{gymId}/anuncios/{id}")
    public Map<String, Object> editar(@PathVariable String gymId, @PathVariable String id,
                                      @RequestBody AnuncioService.Solicitud solicitud,
                                      @RequestHeader(value = AccesoService.ENCABEZADO, required = false) String userId) {
        acceso.exigirDueno(gymId, userId);
        return anuncios.guardar(gymId, id, solicitud);
    }

    // {"pausado": true|false}
    @PostMapping("/api/gyms/{gymId}/anuncios/{id}/pausa")
    public Map<String, Object> pausar(@PathVariable String gymId, @PathVariable String id,
                                      @RequestBody Map<String, Boolean> cuerpo,
                                      @RequestHeader(value = AccesoService.ENCABEZADO, required = false) String userId) {
        acceso.exigirDueno(gymId, userId);
        return anuncios.pausar(gymId, id, Boolean.TRUE.equals(cuerpo.get("pausado")));
    }

    @DeleteMapping("/api/gyms/{gymId}/anuncios/{id}")
    public ResponseEntity<?> borrar(@PathVariable String gymId, @PathVariable String id,
                                    @RequestHeader(value = AccesoService.ENCABEZADO, required = false) String userId) {
        acceso.exigirDueno(gymId, userId);
        anuncios.borrar(gymId, id);
        return ResponseEntity.ok(Map.of("message", "Anuncio borrado."));
    }

    // GET /api/anuncios → los vigentes del gimnasio de quien tiene la sesión (la app).
    @GetMapping("/api/anuncios")
    public List<Map<String, Object>> vigentes(@RequestHeader(value = AccesoService.ENCABEZADO, required = false) String userId) {
        return anuncios.vigentesPara(acceso.exigirUsuario(userId));
    }
}
