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

    // POST /api/lectores/voz  {"comando": "entrada|salida", "confianza": 0.93}
    //   La XIAO reconoció un comando de voz (con su botón presionado) y prendió
    //   o apagó su LED. Queda en el historial y el panel muestra cómo está el LED.
    @PostMapping("/voz")
    public Map<String, Object> voz(@RequestHeader(value = ControlAccesoService.LLAVE, required = false) String llave,
                                   @RequestBody Map<String, Object> body) {
        Lector lector = control.lectorDe(llave);
        Object confianza = body.get("confianza");
        return control.registrarVoz(lector, body.get("comando") == null ? null : body.get("comando").toString(),
                confianza instanceof Number n ? n.doubleValue() : null);
    }

    // GET /api/lectores/comandos?espera=15 → espera hasta "espera" segundos (0 a 15)
    // a que el dueño abra o niegue desde el panel: {"comando": "abrir|negar", ...},
    // o 204 si no hubo nada. Por WiFi el lector espera los 15 s; por LTE o 2G
    // pregunta con espera=0 cada pocos segundos, para no tener el módem ocupado.
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

    // GET /api/lectores/aforo → {"dentro": 3}: cuántos hay en el gimnasio (con tag o por voz).
    @GetMapping("/aforo")
    public Map<String, Object> aforo(@RequestHeader(value = ControlAccesoService.LLAVE, required = false) String llave) {
        return control.aforo(control.lectorDe(llave));
    }

    // GET /api/lectores/lista → los tags del gimnasio, para decidir sin internet.
    @GetMapping("/lista")
    public List<Map<String, Object>> lista(@RequestHeader(value = ControlAccesoService.LLAVE, required = false) String llave) {
        return control.lista(control.lectorDe(llave));
    }
}
