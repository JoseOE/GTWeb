package com.gymtrack.util;

import java.security.SecureRandom;

// Ids de la tienda con la misma forma que usaba Medusa: prefijo + "_" + un ULID
// (26 caracteres: los primeros 10 son la hora y los otros 16 son al azar), p. ej.
// prod_01M41JH0VFNNPKYQ523Q507WYZ. Ordenan por fecha de creación y conviven con
// los ids que se traen de Medusa, así que el ticket guardado en el navegador,
// los recibos y la app no distinguen unos de otros.
public final class Ids {

    private static final char[] BASE32 = "0123456789ABCDEFGHJKMNPQRSTVWXYZ".toCharArray();
    private static final SecureRandom AZAR = new SecureRandom();

    private Ids() {}

    public static String nuevo(String prefijo) {
        return prefijo + "_" + ulid(System.currentTimeMillis());
    }

    static String ulid(long milisegundos) {
        char[] c = new char[26];
        long tiempo = milisegundos;
        for (int i = 9; i >= 0; i--) {
            c[i] = BASE32[(int) (tiempo & 31)];
            tiempo >>>= 5;
        }
        byte[] bytes = new byte[10];
        AZAR.nextBytes(bytes);
        // 80 bits al azar → 16 caracteres de 5 bits.
        long alto = 0;
        for (int i = 0; i < 5; i++) alto = (alto << 8) | (bytes[i] & 0xFF);
        long bajo = 0;
        for (int i = 5; i < 10; i++) bajo = (bajo << 8) | (bytes[i] & 0xFF);
        for (int i = 17; i >= 10; i--) {
            c[i] = BASE32[(int) (alto & 31)];
            alto >>>= 5;
        }
        for (int i = 25; i >= 18; i--) {
            c[i] = BASE32[(int) (bajo & 31)];
            bajo >>>= 5;
        }
        return new String(c);
    }
}
