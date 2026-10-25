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

import java.util.Map;

// Cliente de la API de Stripe (modo de prueba): Checkout Sessions, captura y
// reembolsos de PaymentIntents. Stripe recibe formularios (clave[sub]=valor) y
// responde JSON. La llave secreta sale de STRIPE_SECRET_KEY y nunca se escribe
// en los registros.
@Service
public class StripeApi {

    private static final Logger log = LoggerFactory.getLogger(StripeApi.class);
    private static final String BASE = "https://api.stripe.com/v1";

    private final RestClient http = RestClient.create();
    private final ObjectMapper json;
    private final String secreta;

    public StripeApi(ObjectMapper json, @Value("${stripe.secret-key:}") String secreta) {
        this.json = json;
        this.secreta = secreta == null ? "" : secreta.trim();
    }

    public boolean configurada() {
        return !secreta.isEmpty();
    }

    public Map<String, Object> post(String ruta, MultiValueMap<String, String> form) {
        return llamar(() -> http.post().uri(BASE + ruta)
                .header("Authorization", "Bearer " + secreta)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form == null ? new LinkedMultiValueMap<>() : form)
                .retrieve().body(String.class), ruta);
    }

    public Map<String, Object> get(String rutaConConsulta) {
        return llamar(() -> http.get().uri(BASE + rutaConConsulta)
                .header("Authorization", "Bearer " + secreta)
                .retrieve().body(String.class), rutaConConsulta);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> llamar(java.util.function.Supplier<String> llamada, String ruta) {
        if (!configurada()) {
            throw new TiendaException(HttpStatus.SERVICE_UNAVAILABLE, "El pago con tarjeta no está disponible: falta configurar Stripe.");
        }
        try {
            return json.readValue(llamada.get(), Map.class);
        } catch (RestClientResponseException e) {
            String mensaje = mensajeDe(e.getResponseBodyAsString());
            log.warn("Stripe respondió {} en {}: {}", e.getStatusCode().value(), ruta.replaceAll("\\?.*", ""), mensaje);
            throw new ErrorStripe(e.getStatusCode().value(), mensaje, codigoDe(e.getResponseBodyAsString()));
        } catch (ErrorStripe | TiendaException e) {
            throw e;
        } catch (Exception e) {
            log.warn("No se pudo hablar con Stripe ({}): {}", ruta.replaceAll("\\?.*", ""), e.getMessage());
            throw new TiendaException(HttpStatus.BAD_GATEWAY, "No pudimos comunicarnos con Stripe. Intenta de nuevo en un momento.");
        }
    }

    @SuppressWarnings("unchecked")
    private String mensajeDe(String cuerpo) {
        try {
            Map<String, Object> error = (Map<String, Object>) json.readValue(cuerpo, Map.class).get("error");
            return error == null ? cuerpo : String.valueOf(error.get("message"));
        } catch (Exception e) {
            return cuerpo;
        }
    }

    @SuppressWarnings("unchecked")
    private String codigoDe(String cuerpo) {
        try {
            Map<String, Object> error = (Map<String, Object>) json.readValue(cuerpo, Map.class).get("error");
            Object codigo = error == null ? null : (error.get("decline_code") != null ? error.get("decline_code") : error.get("code"));
            return codigo == null ? "" : codigo.toString();
        } catch (Exception e) {
            return "";
        }
    }

    // Error que respondió Stripe (tarjeta rechazada, sesión que no existe...).
    public static class ErrorStripe extends RuntimeException {
        private final int status;
        private final String codigo;

        ErrorStripe(int status, String mensaje, String codigo) {
            super(mensaje);
            this.status = status;
            this.codigo = codigo;
        }

        public int getStatus() { return status; }
        public String getCodigo() { return codigo; }
    }
}
