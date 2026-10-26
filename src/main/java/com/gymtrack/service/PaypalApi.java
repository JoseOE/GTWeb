package com.gymtrack.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

// Cliente de la API REST de PayPal (Orders v2). PAYPAL_MODO=sandbox usa
// api-m.sandbox.paypal.com; cualquier otro valor, la de producción. El token
// de acceso (OAuth, client credentials) se pide una vez y se reusa hasta que vence.
@Service
public class PaypalApi {

    private static final Logger log = LoggerFactory.getLogger(PaypalApi.class);

    private final RestClient http = RestClient.create();
    private final ObjectMapper json;
    private final String clientId;
    private final String secreto;
    private final String base;

    private String token;
    private Instant tokenVence = Instant.EPOCH;

    public PaypalApi(ObjectMapper json,
                     @Value("${paypal.client-id:}") String clientId,
                     @Value("${paypal.client-secret:}") String secreto,
                     @Value("${paypal.modo:sandbox}") String modo) {
        this.json = json;
        this.clientId = clientId == null ? "" : clientId.trim();
        this.secreto = secreto == null ? "" : secreto.trim();
        this.base = "live".equalsIgnoreCase(modo == null ? "" : modo.trim())
                ? "https://api-m.paypal.com" : "https://api-m.sandbox.paypal.com";
    }

    public boolean configurada() {
        return !clientId.isEmpty() && !secreto.isEmpty();
    }

    // POST con cuerpo JSON. idempotencia: el mismo valor no repite la operación
    // en PayPal (por si la respuesta se pierde y se reintenta).
    public Map<String, Object> post(String ruta, Object cuerpo, String idempotencia) {
        return llamar(() -> http.post().uri(base + ruta)
                .header("Authorization", "Bearer " + token())
                .header("PayPal-Request-Id", idempotencia == null ? UUID.randomUUID().toString() : idempotencia)
                .header("Prefer", "return=representation")
                .contentType(MediaType.APPLICATION_JSON)
                .body(cuerpo == null ? Map.of() : cuerpo)
                .retrieve().body(String.class), ruta);
    }

    public Map<String, Object> get(String ruta) {
        return llamar(() -> http.get().uri(base + ruta)
                .header("Authorization", "Bearer " + token())
                .retrieve().body(String.class), ruta);
    }

    @SuppressWarnings("unchecked")
    private synchronized String token() {
        if (!configurada()) {
            throw new TiendaException(HttpStatus.SERVICE_UNAVAILABLE, "El pago con PayPal no está disponible: falta configurar PayPal.");
        }
        if (token != null && Instant.now().isBefore(tokenVence)) return token;
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "client_credentials");
        String basica = Base64.getEncoder().encodeToString((clientId + ":" + secreto).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        try {
            Map<String, Object> r = json.readValue(http.post().uri(base + "/v1/oauth2/token")
                    .header("Authorization", "Basic " + basica)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form).retrieve().body(String.class), Map.class);
            token = String.valueOf(r.get("access_token"));
            long segundos = r.get("expires_in") instanceof Number n ? n.longValue() : 3000;
            // Un minuto antes de que venza se pide otro.
            tokenVence = Instant.now().plusSeconds(Math.max(60, segundos - 60));
            return token;
        } catch (RestClientResponseException e) {
            log.warn("PayPal rechazó las credenciales ({}).", e.getStatusCode().value());
            throw new TiendaException(HttpStatus.SERVICE_UNAVAILABLE, "El pago con PayPal no está disponible por ahora. Intenta con otro método.");
        } catch (Exception e) {
            log.warn("No se pudo pedir el token de PayPal: {}", e.getMessage());
            throw new TiendaException(HttpStatus.BAD_GATEWAY, "No pudimos comunicarnos con PayPal. Intenta de nuevo en un momento.");
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> llamar(java.util.function.Supplier<String> llamada, String ruta) {
        try {
            String cuerpo = llamada.get();
            return cuerpo == null || cuerpo.isBlank() ? Map.of() : json.readValue(cuerpo, Map.class);
        } catch (RestClientResponseException e) {
            String problema = problemaDe(e.getResponseBodyAsString());
            log.warn("PayPal respondió {} en {}: {}", e.getStatusCode().value(), ruta, problema);
            throw new ErrorPaypal(e.getStatusCode().value(), problema);
        } catch (ErrorPaypal | TiendaException e) {
            throw e;
        } catch (Exception e) {
            log.warn("No se pudo hablar con PayPal ({}): {}", ruta, e.getMessage());
            throw new TiendaException(HttpStatus.BAD_GATEWAY, "No pudimos comunicarnos con PayPal. Intenta de nuevo en un momento.");
        }
    }

    // El "issue" del primer detalle (INSTRUMENT_DECLINED, ORDER_NOT_APPROVED...) o el nombre del error.
    @SuppressWarnings("unchecked")
    private String problemaDe(String cuerpo) {
        try {
            Map<String, Object> r = json.readValue(cuerpo, Map.class);
            if (r.get("details") instanceof List<?> l && !l.isEmpty() && l.get(0) instanceof Map<?, ?> d && d.get("issue") != null) {
                return String.valueOf(d.get("issue"));
            }
            return String.valueOf(r.getOrDefault("name", r.getOrDefault("error", "ERROR")));
        } catch (Exception e) {
            return "ERROR";
        }
    }

    public static class ErrorPaypal extends RuntimeException {
        private final int status;

        ErrorPaypal(int status, String problema) {
            super(problema);
            this.status = status;
        }

        public int getStatus() { return status; }
    }
}
