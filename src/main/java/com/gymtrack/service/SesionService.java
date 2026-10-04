package com.gymtrack.service;

import com.gymtrack.model.Sesion;
import com.gymtrack.repository.SesionRepository;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;

// Sesiones de la página y la app: un token al azar por inicio de sesión.
// Ver Sesion y SesionFilter.
@Service
public class SesionService {

    public static final Duration VIGENCIA = Duration.ofDays(30);
    private static final SecureRandom AZAR = new SecureRandom();

    private final SesionRepository sesiones;

    public SesionService(SesionRepository sesiones) {
        this.sesiones = sesiones;
    }

    // Abre una sesión y devuelve el token. Es lo único que se le da al
    // navegador; aquí solo queda su huella.
    public String abrir(String userId) {
        byte[] bytes = new byte[32];
        AZAR.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Instant ahora = Instant.now();
        sesiones.save(new Sesion(huella(token), userId, ahora, ahora.plus(VIGENCIA)));
        return token;
    }

    // El usuario de una sesión vigente, o vacío si el token no existe o venció
    // (Mongo borra las vencidas cada minuto; mientras tanto se revisa aquí).
    public Optional<String> usuarioDe(String token) {
        if (token == null || token.isBlank()) return Optional.empty();
        return sesiones.findById(huella(token.trim()))
                .filter(s -> s.getExpiraEn() != null && s.getExpiraEn().isAfter(Instant.now()))
                .map(Sesion::getUserId);
    }

    public void cerrar(String token) {
        if (token != null && !token.isBlank()) sesiones.deleteById(huella(token.trim()));
    }

    // Al restablecer la contraseña: nadie que la conociera antes sigue dentro.
    public void cerrarTodas(String userId) {
        sesiones.deleteAll(sesiones.findByUserId(userId));
    }

    // Al cambiarla desde "Mi cuenta": se cierran las demás, no la de quien la cambió.
    public void cerrarOtras(String userId, String tokenActual) {
        String actual = tokenActual == null ? "" : huella(tokenActual.trim());
        sesiones.deleteAll(sesiones.findByUserId(userId).stream().filter(s -> !s.getId().equals(actual)).toList());
    }

    static String huella(String token) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible", e);
        }
    }
}
