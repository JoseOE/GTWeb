package com.gymtrack.util;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

// Algoritmo nativo de Java para encriptar la contraseña (SHA-256).
// Se implementa así para mantener el backend ligero sin dependencias extra como Spring Security.
public final class PasswordUtil {

    private PasswordUtil() {}

    public static String hash(String password) {
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
            throw new RuntimeException("Error al encriptar la contraseña", e);
        }
    }
}
