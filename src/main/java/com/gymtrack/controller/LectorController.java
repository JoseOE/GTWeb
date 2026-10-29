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

    // POST /api/lectores/rostro  {"etiqueta": "chema", "confianza": 0.93}
    //   La XIAO reconoció una cara. → {"resultado": "ENTRADA|SALIDA|NEGADO|IGNORADO|DESCONOCIDO", ...}
    @PostMapping("/rostro")
    public Map<String, Object> rostro(@RequestHeader(value = ControlAccesoService.LLAVE, required = false) String llave,
                                      @RequestBody Map<String, Object> body) {
        Lector lector = control.lectorDe(llave);
        Object confianza = body.get("confianza");
        return control.registrarRostro(lector, body.get("etiqueta") == null ? null : body.get("etiqueta").toString(),
                confianza instanceof Number n ? n.doubleValue() : null);
    }

    // GET /api/lectores/comandos?espera=15 → espera hasta "espera" segundos (0 a 15)
    // a que haya algo que hacer: {"comando": "abrir|negar", ...}, o 204 si no hubo nada.
    // Por WiFi el lector espera los 15 s; por LTE o 2G pregunta con espera=0 cada
    // pocos segundos, para no tener el módem ocupado.
    @GetMapping("/comandos")
    public ResponseEntity<Map<String, Object>> comandos(@RequestHeader(value = ControlAccesoService.LLAVE, required = false) String llave,
                                                        @RequestParam(defaultValue = "15") int espera) {
        Lector lector = control.lectorDe(llave);
        return control.esperarComando(lector, Duration.ofSeconds(Math.max(0, Math.min(espera, 15))))
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
