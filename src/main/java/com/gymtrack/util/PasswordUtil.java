package com.gymtrack.util;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.regex.Pattern;

// Cifrado de contraseñas con BCrypt: cada una lleva su propia "sal" y el cálculo es
// lento a propósito, así que no se pueden adivinar comparando contra listas.
// Solo se usa el módulo de cifrado de Spring Security, sin el resto del framework.
//
// Las cuentas creadas antes guardaban SHA-256 sin sal. Se siguen aceptando y se
// pasan a BCrypt la siguiente vez que el usuario inicia sesión.
public final class PasswordUtil {

    private static final BCryptPasswordEncoder BCRYPT = new BCryptPasswordEncoder();
    private static final Pattern SHA256_HEX = Pattern.compile("[0-9a-f]{64}");
    // 8+ caracteres con al menos una letra, un número y un símbolo (igual que registro.html).
    private static final Pattern SEGURA = Pattern.compile("^(?=.*[a-zA-Z])(?=.*\\d)(?=.*[\\W_]).{8,}$");

    private PasswordUtil() {}

    public static String hash(String password) {
        return BCRYPT.encode(password);
    }

    public static boolean coincide(String password, String guardada) {
        if (password == null || guardada == null) return false;
        if (esFormatoAnterior(guardada)) {
            return MessageDigest.isEqual(
                    sha256Anterior(password).getBytes(StandardCharsets.UTF_8),
                    guardada.getBytes(StandardCharsets.UTF_8));
        }
        return BCRYPT.matches(password, guardada);
    }

    // true si la contraseña guardada todavía está en SHA-256 y hay que pasarla a BCrypt.
    public static boolean necesitaActualizarse(String guardada) {
        return guardada != null && esFormatoAnterior(guardada);
    }

    public static boolean esSegura(String password) {
        return password != null && SEGURA.matcher(password).matches();
    }

    private static boolean esFormatoAnterior(String guardada) {
        return SHA256_HEX.matcher(guardada).matches();
    }

    // Mismo cálculo que hacía la versión anterior (incluido el charset por defecto),
    // para que las contraseñas con acentos o ñ sigan coincidiendo.
    private static String sha256Anterior(String password) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(password.getBytes());
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible", e);
        }
    }
}
