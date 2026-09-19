package com.gymtrack.util;

import java.security.SecureRandom;

// Código que el gimnasio le da al usuario para que se una desde la app.
public final class JoinCodeUtil {

    // Sin I, O, 0, 1: se confunden al leerlos de un cartel en recepción.
    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private JoinCodeUtil() {}

    // Formato XXXX-XXXX, p. ej. "PWR4-K7M2"
    public static String generate() {
        StringBuilder sb = new StringBuilder(9);
        for (int i = 0; i < 8; i++) {
            if (i == 4) sb.append('-');
            sb.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }

    // Acepta que el usuario lo escriba en minúsculas, con espacios o sin el guion.
    public static String normalize(String raw) {
        if (raw == null) return null;
        String clean = raw.toUpperCase().replaceAll("[^A-Z0-9]", "");
        if (clean.length() != 8) return clean;
        return clean.substring(0, 4) + "-" + clean.substring(4);
    }
}
