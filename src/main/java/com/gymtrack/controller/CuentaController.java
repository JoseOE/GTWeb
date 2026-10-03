package com.gymtrack.controller;

import com.gymtrack.model.User;
import com.gymtrack.service.AccesoService;
import com.gymtrack.service.CuentaException;
import com.gymtrack.service.CuentaService;
import com.gymtrack.service.SesionService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

// Trámites de la cuenta que se confirman por correo.
@RestController
@RequestMapping("/api/cuenta")
public class CuentaController {

    private final CuentaService cuentaService;
    private final SesionService sesiones;

    public CuentaController(CuentaService cuentaService, SesionService sesiones) {
        this.cuentaService = cuentaService;
        this.sesiones = sesiones;
    }

    // POST /api/cuenta/verificar → con {email, codigo} (código escrito a mano)
    // o con {token} (el botón del correo).
    // Verificar demuestra que el correo es suyo, así que la cuenta entra directo:
    // la respuesta trae la sesión y lo que la página guarda al iniciar sesión.
    @PostMapping("/verificar")
    public ResponseEntity<?> verificar(@RequestBody Map<String, String> body) {
        String token = body.get("token");
        User user = token != null && !token.isBlank()
                ? cuentaService.verificarConEnlace(token)
                : cuentaService.verificarConCodigo(body.get("email"), body.get("codigo"));
        Map<String, Object> respuesta = new LinkedHashMap<>();
        respuesta.put("message", "¡Correo verificado! Entrando a tu panel…");
        respuesta.put("email", user.getEmail());
        respuesta.put("token", sesiones.abrir(user.getId()));
        respuesta.put("id", user.getId());
        respuesta.put("nombre", user.getNombre());
        respuesta.put("role", user.getRole());
        respuesta.put("gymId", user.getGymId());
        return ResponseEntity.ok(respuesta);
    }

    // POST /api/cuenta/verificar/reenviar → manda un código nuevo (uno por minuto).
    @PostMapping("/verificar/reenviar")
    public ResponseEntity<?> reenviarVerificacion(@RequestBody Map<String, String> body) {
        cuentaService.reenviarVerificacion(body.get("email"));
        return ResponseEntity.ok(Map.of("message", "Si tu cuenta tiene la verificación pendiente, te enviamos un código nuevo."));
    }

    // POST /api/cuenta/recuperar → manda el enlace para restablecer la contraseña.
    // La respuesta es la misma aunque el correo no exista.
    @PostMapping("/recuperar")
    public ResponseEntity<?> recuperar(@RequestBody Map<String, String> body) {
        cuentaService.solicitarRecuperacion(body.get("email"));
        return ResponseEntity.ok(Map.of("message",
                "Si hay una cuenta con ese correo, te enviamos un enlace para restablecer tu contraseña. Revisa también la carpeta de spam."));
    }

    // POST /api/cuenta/restablecer → {token, password} desde restablecer.html.
    @PostMapping("/restablecer")
    public ResponseEntity<?> restablecer(@RequestBody Map<String, String> body) {
        cuentaService.restablecerContrasena(body.get("token"), body.get("password"));
        return ResponseEntity.ok(Map.of("message", "Tu contraseña se actualizó. Ya puedes iniciar sesión."));
    }

    // POST /api/cuenta/contrasena → {userId, actual, nueva} desde Mi cuenta.
    // Las demás sesiones de la cuenta se cierran; la de quien la cambió sigue.
    @PostMapping("/contrasena")
    public ResponseEntity<?> cambiarContrasena(@RequestBody Map<String, String> body,
                                               @RequestHeader(value = AccesoService.ENCABEZADO, required = false) String sesion,
                                               @RequestHeader(value = "Authorization", required = false) String autorizacion) {
        exigirMismaCuenta(body.get("userId"), sesion);
        String tokenActual = autorizacion != null && autorizacion.regionMatches(true, 0, "Bearer ", 0, 7) ? autorizacion.substring(7) : null;
        cuentaService.cambiarContrasena(body.get("userId"), body.get("actual"), body.get("nueva"), tokenActual);
        return ResponseEntity.ok(Map.of("message", "Tu contraseña se actualizó."));
    }

    // POST /api/cuenta/correo → {userId, password, nuevoCorreo}: manda un código al correo nuevo.
    @PostMapping("/correo")
    public ResponseEntity<?> solicitarCambioDeCorreo(@RequestBody Map<String, String> body,
                                                     @RequestHeader(value = AccesoService.ENCABEZADO, required = false) String sesion) {
        exigirMismaCuenta(body.get("userId"), sesion);
        cuentaService.solicitarCambioDeCorreo(body.get("userId"), body.get("password"), body.get("nuevoCorreo"));
        return ResponseEntity.ok(Map.of("message", "Te enviamos un código al correo nuevo. Escríbelo para confirmar el cambio."));
    }

    // POST /api/cuenta/correo/confirmar → {userId, codigo}: aplica el cambio.
    @PostMapping("/correo/confirmar")
    public ResponseEntity<?> confirmarCambioDeCorreo(@RequestBody Map<String, String> body,
                                                     @RequestHeader(value = AccesoService.ENCABEZADO, required = false) String sesion) {
        exigirMismaCuenta(body.get("userId"), sesion);
        User user = cuentaService.confirmarCambioDeCorreo(body.get("userId"), body.get("codigo"));
        return ResponseEntity.ok(Map.of("message", "Listo, tu correo ahora es " + user.getEmail() + ".", "email", user.getEmail()));
    }

    // Con sesión, solo se cambia la propia cuenta. (Sin sesión, durante la
    // transición de la app, siguen pidiendo la contraseña o el código del correo.)
    private static void exigirMismaCuenta(String userId, String sesion) {
        if (sesion != null && !sesion.isBlank() && !sesion.equals(userId)) {
            throw new CuentaException(HttpStatus.FORBIDDEN, "Solo puedes cambiar tu propia cuenta.");
        }
    }

    @ExceptionHandler(CuentaException.class)
    public ResponseEntity<Map<String, String>> manejarError(CuentaException e) {
        return ResponseEntity.status(e.getStatus()).body(Map.of("error", e.getMessage()));
    }
}
