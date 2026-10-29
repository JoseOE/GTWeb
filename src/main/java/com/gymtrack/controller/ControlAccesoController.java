package com.gymtrack.controller;

import com.gymtrack.model.FotoAcceso;
import com.gymtrack.service.AccesoService;
import com.gymtrack.service.ControlAccesoService;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

// Pestaña "Accesos RFID" del panel: lectores, tags de los socios, alertas de
// accesos negados (con las fotos de la XIAO) y la bitácora de la puerta.
// Solo el dueño del gimnasio.
@RestController
@RequestMapping("/api/gyms/{gymId}/acceso")
public class ControlAccesoController {

    private final ControlAccesoService control;
    private final AccesoService acceso;

    public ControlAccesoController(ControlAccesoService control, AccesoService acceso) {
        this.control = control;
        this.acceso = acceso;
    }

    @GetMapping
    public Map<String, Object> resumen(@PathVariable String gymId,
                                       @RequestHeader(value = AccesoService.ENCABEZADO, required = false) String userId) {
        acceso.exigirDueno(gymId, userId);
        return control.resumen(gymId);
    }

    // POST /api/gyms/{gymId}/acceso/lectores  {"nombre": "Puerta principal"} → {id, nombre, llave}
    @PostMapping("/lectores")
    public Map<String, Object> crearLector(@PathVariable String gymId,
                                           @RequestHeader(value = AccesoService.ENCABEZADO, required = false) String userId,
                                           @RequestBody(required = false) Map<String, String> body) {
        acceso.exigirDueno(gymId, userId);
        return control.crearLector(gymId, body == null ? null : body.get("nombre"));
    }

    // POST /api/gyms/{gymId}/acceso/tarjetas  {"epc": "E200...", "userId": "..."}
    @PostMapping("/tarjetas")
    public Map<String, Object> ligar(@PathVariable String gymId,
                                     @RequestHeader(value = AccesoService.ENCABEZADO, required = false) String userId,
                                     @RequestBody Map<String, String> body) {
        acceso.exigirDueno(gymId, userId);
        return control.ligarTarjeta(gymId, body.get("epc"), body.get("userId"), userId);
    }

    // Modo registro: el siguiente tag que pase por ese lector se captura (60 s).
    @PostMapping("/lectores/{lectorId}/registro")
    public ResponseEntity<Void> iniciarRegistro(@PathVariable String gymId, @PathVariable String lectorId,
                                                @RequestHeader(value = AccesoService.ENCABEZADO, required = false) String userId) {
        acceso.exigirDueno(gymId, userId);
        control.iniciarRegistro(gymId, lectorId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/lectores/{lectorId}/registro")
    public ResponseEntity<Void> cancelarRegistro(@PathVariable String gymId, @PathVariable String lectorId,
                                                 @RequestHeader(value = AccesoService.ENCABEZADO, required = false) String userId) {
        acceso.exigirDueno(gymId, userId);
        control.cancelarRegistro(gymId, lectorId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/tarjetas/{id}")
    public ResponseEntity<Void> quitar(@PathVariable String gymId, @PathVariable String id,
                                       @RequestHeader(value = AccesoService.ENCABEZADO, required = false) String userId) {
        acceso.exigirDueno(gymId, userId);
        control.quitarTarjeta(gymId, id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/solicitudes/{id}/abrir")
    public Map<String, Object> abrir(@PathVariable String gymId, @PathVariable String id,
                                     @RequestHeader(value = AccesoService.ENCABEZADO, required = false) String userId) {
        acceso.exigirDueno(gymId, userId);
        return control.atender(gymId, id, true, userId);
    }

    @PostMapping("/solicitudes/{id}/negar")
    public Map<String, Object> negar(@PathVariable String gymId, @PathVariable String id,
                                     @RequestHeader(value = AccesoService.ENCABEZADO, required = false) String userId) {
        acceso.exigirDueno(gymId, userId);
        return control.atender(gymId, id, false, userId);
    }

    // Las fotos no tienen URL pública: el panel las pide con su sesión.
    @GetMapping("/fotos/{id}")
    public ResponseEntity<byte[]> foto(@PathVariable String gymId, @PathVariable String id,
                                       @RequestHeader(value = AccesoService.ENCABEZADO, required = false) String userId) {
        acceso.exigirDueno(gymId, userId);
        FotoAcceso f = control.foto(gymId, id);
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_JPEG)
                .cacheControl(CacheControl.noStore())
                .body(f.getDatos());
    }
}
