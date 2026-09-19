package com.gymtrack.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

// Genera los códigos y enlaces de los correos de la cuenta y los compara
// contra su hash guardado en MongoDB.
public final class TokenUtil {

    private static final SecureRandom RANDOM = new SecureRandom();

    private TokenUtil() {}

    // Código de 6 dígitos para escribir a mano (000000–999999).
    public static String codigo() {
        return String.format("%06d", RANDOM.nextInt(1_000_000));
    }

    // Token largo e imposible de adivinar para los enlaces del correo.
    public static String enlace() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public static String sha256(String valor) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(valor.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible", e);
        }
    }

    // Comparación en tiempo constante: no revela cuántos caracteres coinciden.
    public static boolean coincide(String valor, String hashGuardado) {
        if (valor == null || hashGuardado == null) return false;
        return MessageDigest.isEqual(
                sha256(valor).getBytes(StandardCharsets.UTF_8),
                hashGuardado.getBytes(StandardCharsets.UTF_8));
    }
}
