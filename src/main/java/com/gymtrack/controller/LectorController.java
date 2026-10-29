package com.gymtrack.controller;

import com.gymtrack.model.Lector;
import com.gymtrack.service.ControlAccesoService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.List;
import java.util.Map;

// Lo que llama el lector de la puerta (LILYGO) y su cámara (XIAO). No usan
// sesión: se identifican con la llave del lector en X-Lector-Key.
@RestController
@RequestMapping("/api/lectores")
public class LectorController {

    private final ControlAccesoService control;

    public LectorController(ControlAccesoService control) {
        this.control = control;
    }

    // POST /api/lectores/accesos  {"epc": "E200..."}
    //   → {"resultado": "ENTRADA|SALIDA|NEGADO|IGNORADO", "nombre", "motivo", "solicitudId"}
    @PostMapping("/accesos")
    public Map<String, Object> lectura(@RequestHeader(value = ControlAccesoService.LLAVE, required = false) String llave,
                                       @RequestBody Map<String, String> body) {
        Lector lector = control.lectorDe(llave);
        return control.registrarLectura(lector, body.get("epc"));
    }

    // GET /api/lectores/comandos → espera hasta 15 s a que el dueño abra o
    // niegue desde el panel: {"comando": "abrir|negar", ...}, o 204 si no hubo nada.
    @GetMapping("/comandos")
    public ResponseEntity<Map<String, Object>> comandos(@RequestHeader(value = ControlAccesoService.LLAVE, required = false) String llave) {
        Lector lector = control.lectorDe(llave);
        return control.esperarComando(lector, Duration.ofSeconds(15))
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    // POST /api/lectores/fotos  (cuerpo: el JPEG tal cual) → {"id", "solicitudId"}
    @PostMapping("/fotos")
    public Map<String, Object> foto(@RequestHeader(value = ControlAccesoService.LLAVE, required = false) String llave,
                                    @RequestBody byte[] jpeg) {
        Lector lector = control.lectorDe(llave);
        return control.guardarFoto(lector, jpeg);
    }

    // GET /api/lectores/lista → los tags del gimnasio, para decidir sin internet.
    @GetMapping("/lista")
    public List<Map<String, Object>> lista(@RequestHeader(value = ControlAccesoService.LLAVE, required = false) String llave) {
        return control.lista(control.lectorDe(llave));
    }
}
