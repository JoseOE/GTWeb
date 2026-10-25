package com.gymtrack.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

// Cliente de la API de Openpay (BBVA), que opera los pagos en efectivo de
// Paynet: cargos en tienda con su referencia y código de barras. OPENPAY_MODO=
// sandbox usa sandbox-api.openpay.mx; cualquier otro valor, producción.
// Autenticación básica con la llave privada como usuario y sin contraseña.
@Service
public class OpenpayApi {

    private static final Logger log = LoggerFactory.getLogger(OpenpayApi.class);

    private final RestClient http = RestClient.create();
    private final ObjectMapper json;
    private final String merchantId;
    private final String privada;
    private final boolean sandbox;

    public OpenpayApi(ObjectMapper json,
                      @Value("${openpay.merchant-id:}") String merchantId,
                      @Value("${openpay.private-key:}") String privada,
                      @Value("${openpay.modo:sandbox}") String modo) {
        this.json = json;
        this.merchantId = merchantId == null ? "" : merchantId.trim();
        this.privada = privada == null ? "" : privada.trim();
        this.sandbox = !"live".equalsIgnoreCase(modo == null ? "" : modo.trim());
    }

    public boolean configurada() {
        return !merchantId.isEmpty() && !privada.isEmpty();
    }

    // La ficha oficial de Paynet en PDF (la genera Openpay con la referencia).
    public String urlFicha(String referencia) {
        return (sandbox ? "https://sandbox-dashboard.openpay.mx" : "https://dashboard.openpay.mx")
                + "/paynet-pdf/" + merchantId + "/" + referencia;
    }

    public Map<String, Object> crearCargo(Map<String, Object> cuerpo) {
        return llamar(() -> http.post().uri(base() + "/charges")
                .header("Authorization", autorizacion())
                .contentType(MediaType.APPLICATION_JSON)
                .body(cuerpo).retrieve().body(String.class), "crear cargo");
    }

    public Map<String, Object> cargo(String id) {
        return llamar(() -> http.get().uri(base() + "/charges/" + id)
                .header("Authorization", autorizacion())
                .retrieve().body(String.class), "consultar cargo");
    }

    private String base() {
        return (sandbox ? "https://sandbox-api.openpay.mx/v1/" : "https://api.openpay.mx/v1/") + merchantId;
    }

    private String autorizacion() {
        return "Basic " + Base64.getEncoder().encodeToString((privada + ":").getBytes(StandardCharsets.UTF_8));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> llamar(java.util.function.Supplier<String> llamada, String que) {
        if (!configurada()) {
            throw new TiendaException(HttpStatus.SERVICE_UNAVAILABLE, "El pago en tiendas no está disponible: falta configurar Openpay.");
        }
        try {
            return json.readValue(llamada.get(), Map.class);
        } catch (RestClientResponseException e) {
            String descripcion = "";
            try {
                Map<String, Object> r = json.readValue(e.getResponseBodyAsString(), Map.class);
                descripcion = r.get("error_code") + " " + r.get("description");
            } catch (Exception ignorado) {
                // Sin cuerpo JSON.
            }
            log.warn("Openpay respondió {} al {}: {}", e.getStatusCode().value(), que, descripcion);
            throw new TiendaException(HttpStatus.BAD_GATEWAY, "Openpay no pudo generar la ficha de pago. Intenta de nuevo o elige otro método.");
        } catch (TiendaException e) {
            throw e;
        } catch (Exception e) {
            log.warn("No se pudo hablar con Openpay ({}): {}", que, e.getMessage());
            throw new TiendaException(HttpStatus.BAD_GATEWAY, "No pudimos comunicarnos con Openpay. Intenta de nuevo en un momento.");
        }
    }
}
