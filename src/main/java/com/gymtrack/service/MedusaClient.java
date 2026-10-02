package com.gymtrack.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.MissingNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collection;
import java.util.StringJoiner;

// Única puerta de Spring hacia Medusa. La web y la app nunca hablan con Medusa:
// llaman a /api/... y Spring decide qué pedirle a Medusa y con qué llave.
//
//  - Admin API (/admin/...): con la llave secreta de MEDUSA_ADMIN_TOKEN, que
//    solo vive en las variables de entorno de Spring.
//  - Store API (/store/...): con la llave publicable del gimnasio
//    (Gym.tienda.publishableKey), que limita todo a su canal de venta.
//
// Las respuestas llegan como JsonNode: Medusa devuelve objetos grandes y aquí
// solo se leen los campos que cada servicio necesita.
@Service
public class MedusaClient {

    private static final Logger log = LoggerFactory.getLogger(MedusaClient.class);
    // Si Medusa no contesta en este tiempo se asume que Render la está despertando.
    private static final int ESPERA_CONEXION_MS = 5_000;
    private static final int ESPERA_RESPUESTA_MS = 25_000;

    private final RestClient http;
    private final ObjectMapper json;
    private final String url;
    private final String autorizacionAdmin;

    public MedusaClient(ObjectMapper json,
                        @Value("${tienda.medusa.url:}") String url,
                        @Value("${tienda.medusa.admin-token:}") String tokenAdmin) {
        this.json = json;
        this.url = url == null ? "" : url.trim().replaceAll("/+$", "");
        String token = tokenAdmin == null ? "" : tokenAdmin.trim();
        // Medusa acepta la llave secreta solo por HTTP Basic: "sk_...:" en base64.
        this.autorizacionAdmin = token.isEmpty() ? ""
                : "Basic " + Base64.getEncoder().encodeToString((token + ":").getBytes(StandardCharsets.UTF_8));

        SimpleClientHttpRequestFactory fabrica = new SimpleClientHttpRequestFactory();
        fabrica.setConnectTimeout(ESPERA_CONEXION_MS);
        fabrica.setReadTimeout(ESPERA_RESPUESTA_MS);
        this.http = RestClient.builder().requestFactory(fabrica).build();
    }

    // false = faltan MEDUSA_URL o MEDUSA_ADMIN_TOKEN: la tienda responde 503 con un mensaje claro.
    public boolean configurada() {
        return !url.isEmpty() && !autorizacionAdmin.isEmpty();
    }

    // ─── Admin API ───

    public JsonNode adminGet(String ruta) {
        return llamar(HttpMethod.GET, ruta, null, null);
    }

    public JsonNode adminPost(String ruta, Object cuerpo) {
        return llamar(HttpMethod.POST, ruta, cuerpo, null);
    }

    public JsonNode adminDelete(String ruta) {
        return llamar(HttpMethod.DELETE, ruta, null, null);
    }

    // ─── Store API (con la llave publicable del gimnasio) ───

    public JsonNode storeGet(String llavePublicable, String ruta) {
        return llamar(HttpMethod.GET, ruta, null, llavePublicable);
    }

    public JsonNode storePost(String llavePublicable, String ruta, Object cuerpo) {
        return llamar(HttpMethod.POST, ruta, cuerpo, llavePublicable);
    }

    public JsonNode storeDelete(String llavePublicable, String ruta) {
        return llamar(HttpMethod.DELETE, ruta, null, llavePublicable);
    }

    // GET /health con espera corta. Lo usa /api/tienda/estado para que la
    // página despierte a Medusa en cuanto se abre, antes de que el dueño la necesite.
    public boolean despierta() {
        if (!configurada()) return false;
        try {
            http.get().uri(URI.create(url + "/health")).retrieve().toBodilessEntity();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // Arma "?campo=valor&..." codificado. Los valores que son colecciones repiten
    // la clave, como espera Medusa: q("type_id[]", List.of(a, b)) → ?type_id[]=a&type_id[]=b
    public static String q(Object... paresClaveValor) {
        StringJoiner partes = new StringJoiner("&", "?", "");
        partes.setEmptyValue("");
        for (int i = 0; i + 1 < paresClaveValor.length; i += 2) {
            String clave = String.valueOf(paresClaveValor[i]);
            Object valor = paresClaveValor[i + 1];
            if (valor == null) continue;
            if (valor instanceof Collection<?> lista) {
                for (Object v : lista) partes.add(codificar(clave) + "=" + codificar(String.valueOf(v)));
            } else {
                partes.add(codificar(clave) + "=" + codificar(String.valueOf(valor)));
            }
        }
        return partes.toString();
    }

    private static String codificar(String texto) {
        return URLEncoder.encode(texto, StandardCharsets.UTF_8);
    }

    private JsonNode llamar(HttpMethod metodo, String ruta, Object cuerpo, String llavePublicable) {
        if (!configurada()) {
            throw new TiendaException(HttpStatus.SERVICE_UNAVAILABLE,
                    "La tienda no está configurada: faltan MEDUSA_URL o MEDUSA_ADMIN_TOKEN.");
        }
        try {
            RestClient.RequestBodySpec peticion = http.method(metodo)
                    .uri(URI.create(url + ruta))
                    .accept(MediaType.APPLICATION_JSON);
            if (llavePublicable != null) {
                peticion = peticion.header("x-publishable-api-key", llavePublicable);
            } else {
                peticion = peticion.header("Authorization", autorizacionAdmin);
            }
            if (cuerpo != null) {
                peticion = peticion.contentType(MediaType.APPLICATION_JSON).body(cuerpo);
            }
            String respuesta = peticion.retrieve().body(String.class);
            return respuesta == null || respuesta.isBlank() ? MissingNode.getInstance() : json.readTree(respuesta);
        } catch (RestClientResponseException e) {
            throw traducir(metodo, ruta, e);
        } catch (ResourceAccessException e) {
            // Tiempo agotado o conexión rechazada: en Render casi siempre es que está dormida.
            log.warn("Medusa no respondió a {} {}: {}", metodo, ruta, e.getMessage());
            throw TiendaException.despertando();
        } catch (TiendaException e) {
            throw e;
        } catch (Exception e) {
            log.error("Respuesta inesperada de Medusa en {} {}", metodo, ruta, e);
            throw new TiendaException(HttpStatus.BAD_GATEWAY, "La tienda respondió algo inesperado. Intenta de nuevo.");
        }
    }

    private TiendaException traducir(HttpMethod metodo, String ruta, RestClientResponseException e) {
        int codigo = e.getStatusCode().value();
        String mensaje = mensajeDeMedusa(e);
        // Mientras Render arranca el servicio, su proxy responde 502/503/504.
        if (codigo == 502 || codigo == 503 || codigo == 504) {
            return TiendaException.despertando();
        }
        if (codigo == 401 || codigo == 403) {
            log.error("Medusa rechazó la llave en {} {}: {}", metodo, ruta, mensaje);
            return new TiendaException(HttpStatus.SERVICE_UNAVAILABLE,
                    "La tienda no está configurada correctamente (llave de Medusa inválida).");
        }
        if (codigo == 404) {
            return new TiendaException(HttpStatus.NOT_FOUND, "No encontramos eso en la tienda.");
        }
        if (codigo >= 400 && codigo < 500) {
            log.warn("Medusa respondió {} a {} {}: {}", codigo, metodo, ruta, mensaje);
            return new TiendaException(HttpStatus.BAD_REQUEST, mensaje);
        }
        log.error("Medusa respondió {} a {} {}: {}", codigo, metodo, ruta, mensaje);
        return new TiendaException(HttpStatus.BAD_GATEWAY, "La tienda tuvo un problema. Intenta de nuevo en un momento.");
    }

    // Medusa responde sus errores como {"type": "...", "message": "..."}.
    private String mensajeDeMedusa(RestClientResponseException e) {
        try {
            String mensaje = json.readTree(e.getResponseBodyAsString()).path("message").asText("");
            return mensaje.isBlank() ? e.getStatusText() : mensaje;
        } catch (Exception ignorada) {
            return e.getStatusText();
        }
    }
}
