package com.gymtrack.controller;

import com.gymtrack.model.User;
import com.gymtrack.service.CuentaException;
import com.gymtrack.service.CuentaService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

// Trámites de la cuenta que se confirman por correo.
@RestController
@RequestMapping("/api/cuenta")
public class CuentaController {

    private final CuentaService cuentaService;

    public CuentaController(CuentaService cuentaService) {
        this.cuentaService = cuentaService;
    }

    // POST /api/cuenta/verificar → con {email, codigo} (código escrito a mano)
    // o con {token} (el botón del correo).
    @PostMapping("/verificar")
    public ResponseEntity<?> verificar(@RequestBody Map<String, String> body) {
        String token = body.get("token");
        User user = token != null && !token.isBlank()
                ? cuentaService.verificarConEnlace(token)
                : cuentaService.verificarConCodigo(body.get("email"), body.get("codigo"));
        return ResponseEntity.ok(Map.of("message", "¡Correo verificado! Ya puedes iniciar sesión.", "email", user.getEmail()));
    }

    // POST /api/cuenta/verificar/reenviar → manda un código nuevo (uno por minuto).
    @PostMapping("/verificar/reenviar")
    public ResponseEntity<?> reenviarVerificacion(@RequestBody Map<String, String> body) {
        cuentaService.reenviarVerificacion(body.get("email"));
        return ResponseEntity.ok(Map.of("message", "Si tu cuenta tiene la verificación pendiente, te enviamos un código nuevo."));
    }

    @ExceptionHandler(CuentaException.class)
    public ResponseEntity<Map<String, String>> manejarError(CuentaException e) {
        return ResponseEntity.status(e.getStatus()).body(Map.of("error", e.getMessage()));
    }
}
