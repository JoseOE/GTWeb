package com.gymtrack.service;

import com.gymtrack.model.TokenTarjeta;
import com.gymtrack.repository.TokenTarjetaRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;

// Simulador de Stripe: tokeniza tarjetas de prueba como lo haría Stripe.js.
//
// La página manda la tarjeta aquí y recibe un token tok_sim_...; al pedido solo
// llega ese token. El número completo y el CVC no se guardan ni se escriben en
// la consola: se validan y se olvidan en esta misma petición.
//
// Solo se aceptan las tarjetas de prueba de Stripe. Cualquier otro número se
// rechaza, para que nadie escriba una tarjeta real "por si acaso".
@Service
public class SimuladorStripeService {

    public static final String APROBADA = "aprobada";

    // Número → marca y lo que pasará al cobrar. Los rechazos se deciden al
    // cobrar (como en Stripe), no al tokenizar: así se prueba el mensaje del checkout.
    private record TarjetaDePrueba(String marca, String resultado) {}

    private static final Map<String, TarjetaDePrueba> TARJETAS = Map.of(
            "4242424242424242", new TarjetaDePrueba("visa", APROBADA),
            "5555555555554444", new TarjetaDePrueba("mastercard", APROBADA),
            "378282246310005", new TarjetaDePrueba("amex", APROBADA),
            "4000000000000002", new TarjetaDePrueba("visa", "rechazada"),
            "4000000000009995", new TarjetaDePrueba("visa", "fondos_insuficientes"),
            "4000000000000069", new TarjetaDePrueba("visa", "vencida"),
            "4000000000000127", new TarjetaDePrueba("visa", "cvc_incorrecto"));

    private static final ZoneId ZONA_MX = ZoneId.of("America/Mexico_City");
    private static final SecureRandom AZAR = new SecureRandom();

    private final TokenTarjetaRepository tokens;

    public SimuladorStripeService(TokenTarjetaRepository tokens) {
        this.tokens = tokens;
    }

    public Map<String, Object> tokenizar(String userId, TarjetaRequest t) {
        String numero = t.getNumero() == null ? "" : t.getNumero().replaceAll("[\\s-]", "");
        if (!numero.matches("\\d{13,19}")) {
            throw error("Escribe el número completo de la tarjeta.");
        }
        if (!pasaLuhn(numero)) {
            throw error("El número de tarjeta no es válido. Revisa que esté bien escrito.");
        }
        TarjetaDePrueba prueba = TARJETAS.get(numero);
        if (prueba == null) {
            throw error("Usa una tarjeta de prueba: este simulador no acepta tarjetas reales.");
        }
        String titular = t.getTitular() == null ? "" : t.getTitular().trim();
        if (titular.isEmpty() || titular.length() > 60) {
            throw error("Escribe el nombre como aparece en la tarjeta.");
        }
        YearMonth vence = vencimiento(t.getMes(), t.getAnio());
        if (vence.isBefore(YearMonth.now(ZONA_MX))) {
            throw error("La fecha de vencimiento ya pasó.");
        }
        int digitosCvc = "amex".equals(prueba.marca()) ? 4 : 3;
        if (t.getCvc() == null || !t.getCvc().matches("\\d{" + digitosCvc + "}")) {
            throw error("El CVC debe tener " + digitosCvc + " dígitos.");
        }

        TokenTarjeta token = new TokenTarjeta();
        token.setToken("tok_sim_" + HexFormat.of().formatHex(bytesAlAzar(12)));
        token.setUserId(userId);
        token.setMarca(prueba.marca());
        token.setUltimos4(numero.substring(numero.length() - 4));
        token.setVencimiento(String.format("%02d/%02d", vence.getMonthValue(), vence.getYear() % 100));
        token.setTitular(titular);
        token.setResultado(prueba.resultado());
        token.setCreadoEn(Instant.now());
        tokens.save(token);

        Map<String, Object> respuesta = new LinkedHashMap<>();
        respuesta.put("token", token.getToken());
        respuesta.put("marca", token.getMarca());
        respuesta.put("ultimos4", token.getUltimos4());
        respuesta.put("vencimiento", token.getVencimiento());
        return respuesta;
    }

    // Cambia los datos que mandó la página por los del token guardado: así el
    // resultado del cobro no se puede inventar desde el navegador. El token se
    // gasta aquí; si el pago no pasa, la página tokeniza de nuevo.
    public Map<String, Object> consumir(String userId, Map<String, Object> datos) {
        Object valor = datos == null ? null : datos.get("token");
        TokenTarjeta token = valor == null ? null : tokens.findById(valor.toString()).orElse(null);
        if (token == null || token.getUsadoEn() != null || !token.getUserId().equals(userId)) {
            throw new TiendaException(HttpStatus.BAD_REQUEST, "Vuelve a capturar los datos de la tarjeta.");
        }
        token.setUsadoEn(Instant.now());
        tokens.save(token);

        Map<String, Object> seguros = new LinkedHashMap<>();
        seguros.put("token", token.getToken());
        seguros.put("marca", token.getMarca());
        seguros.put("ultimos4", token.getUltimos4());
        seguros.put("vencimiento", token.getVencimiento());
        seguros.put("titular", token.getTitular());
        seguros.put("resultado", token.getResultado());
        return seguros;
    }

    // Algoritmo de Luhn: el último dígito de toda tarjeta es una suma de control.
    static boolean pasaLuhn(String numero) {
        int suma = 0;
        boolean doblar = false;
        for (int i = numero.length() - 1; i >= 0; i--) {
            int d = numero.charAt(i) - '0';
            if (doblar) {
                d *= 2;
                if (d > 9) d -= 9;
            }
            suma += d;
            doblar = !doblar;
        }
        return suma % 10 == 0;
    }

    private static YearMonth vencimiento(String mes, String anio) {
        try {
            int m = Integer.parseInt(mes == null ? "" : mes.trim());
            int a = Integer.parseInt(anio == null ? "" : anio.trim());
            if (a < 100) a += 2000;
            if (m < 1 || m > 12 || a > 2100) throw new NumberFormatException();
            return YearMonth.of(a, m);
        } catch (NumberFormatException e) {
            throw error("Escribe el vencimiento como MM/AA.");
        }
    }

    private static byte[] bytesAlAzar(int n) {
        byte[] b = new byte[n];
        AZAR.nextBytes(b);
        return b;
    }

    private static TiendaException error(String mensaje) {
        return new TiendaException(HttpStatus.BAD_REQUEST, mensaje);
    }

    public static class TarjetaRequest {
        private String numero;
        private String titular;
        private String mes;
        private String anio;
        private String cvc;

        public String getNumero() { return numero; }
        public void setNumero(String numero) { this.numero = numero; }

        public String getTitular() { return titular; }
        public void setTitular(String titular) { this.titular = titular; }

        public String getMes() { return mes; }
        public void setMes(String mes) { this.mes = mes; }

        public String getAnio() { return anio; }
        public void setAnio(String anio) { this.anio = anio; }

        public String getCvc() { return cvc; }
        public void setCvc(String cvc) { this.cvc = cvc; }

        // Nunca se imprime la tarjeta, ni por accidente en un log.
        @Override
        public String toString() { return "TarjetaRequest[oculta]"; }
    }
}
