package com.gymtrack.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gymtrack.model.Producto;
import com.gymtrack.repository.ProductoRepository;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.gymtrack.model.Producto.TIPO_MEMBRESIA;
import static com.gymtrack.model.Producto.TIPO_PRODUCTO;

// Migración de un solo uso: trae a MongoDB lo que un gimnasio tenía en Medusa
// (productos, planes y su stock), conservando sus ids, para poder apagar
// Medusa y su base en Neon.
//
//  - La URL y la llave de admin de Medusa llegan en la petición y no se
//    guardan: después de migrar no hacen falta.
//  - No duplica: un producto que ya está en MongoDB se queda como está.
//    Correrla dos veces no cambia nada la segunda.
//  - El stock de cada variante queda como en Medusa: existencias y lo que
//    tenía reservado (apartadas).
@Service
public class MigracionMedusaService {

    private static final Logger log = LoggerFactory.getLogger(MigracionMedusaService.class);
    private static final int POR_PAGINA = 100;
    private static final String CAMPOS_PRODUCTO = String.join(",",
            "id", "title", "description", "status", "thumbnail", "metadata", "type_id", "created_at",
            "categories.handle", "sales_channels.id",
            "variants.id", "variants.title", "variants.manage_inventory", "variants.metadata",
            "variants.created_at", "variants.variant_rank", "variants.prices.amount", "variants.prices.currency_code",
            "variants.inventory_items.inventory.location_levels.location_id",
            "variants.inventory_items.inventory.location_levels.stocked_quantity",
            "variants.inventory_items.inventory.location_levels.reserved_quantity");

    private final ProductoRepository productos;
    private final MongoTemplate mongo;
    private final ObjectMapper json;

    public MigracionMedusaService(ProductoRepository productos, MongoTemplate mongo, ObjectMapper json) {
        this.productos = productos;
        this.mongo = mongo;
        this.json = json;
    }

    public Map<String, Object> migrar(String gymId, String url, String llaveAdmin) {
        Medusa medusa = new Medusa(url, llaveAdmin);
        String canal = canalDeVenta(gymId);

        // ─── Productos y planes ───
        Map<String, String> tipos = new HashMap<>();
        medusa.get("/admin/product-types?fields=id,value&limit=100").path("product_types")
                .forEach(t -> tipos.put(t.path("id").asText(), t.path("value").asText()));
        Map<String, Integer> conteo = new LinkedHashMap<>(Map.of("productosNuevos", 0, "planesNuevos", 0,
                "productosYaEstaban", 0));
        for (JsonNode p : medusa.todos("/admin/products", CAMPOS_PRODUCTO, "products")) {
            if (!delGimnasio(p.path("metadata"), p.path("sales_channels"), null, gymId, canal)) continue;
            Producto existente = productos.findById(p.path("id").asText()).orElse(null);
            if (existente != null) {
                conteo.merge("productosYaEstaban", 1, Integer::sum);
                continue;
            }
            Producto nuevo = producto(p, gymId, tipos);
            productos.insert(nuevo);
            conteo.merge(nuevo.esPlan() ? "planesNuevos" : "productosNuevos", 1, Integer::sum);
        }

        Map<String, Object> r = new LinkedHashMap<>(conteo);
        r.put("avisos", List.of());
        log.info("Migración desde Medusa del gimnasio {}: {}", gymId, conteo);
        return r;
    }

    // ═══════════════════════════ PRODUCTOS ═══════════════════════════

    private Producto producto(JsonNode p, String gymId, Map<String, String> tipos) {
        Producto x = new Producto();
        x.setId(p.path("id").asText());
        x.setGymId(gymId);
        x.setTipo(TIPO_MEMBRESIA.equals(tipos.get(p.path("type_id").asText())) ? TIPO_MEMBRESIA : TIPO_PRODUCTO);
        x.setNombre(p.path("title").asText());
        x.setDescripcion(texto(p.path("description")));
        x.setImagen(texto(p.path("thumbnail")));
        x.setActivo("published".equals(p.path("status").asText()));
        x.setCreadoEn(instante(p.path("created_at")));
        String categoria = texto(p.path("categories").path(0).path("handle"));
        x.setCategoria(x.esPlan() ? TiendaGymService.CATEGORIA_MEMBRESIAS : categoria);

        if (x.esPlan()) {
            JsonNode m = p.path("metadata");
            Producto.Plan plan = new Producto.Plan();
            plan.setPagoUnico(m.path("pagoUnico").asBoolean(false));
            String unidad = texto(m.path("duracionUnidad"));
            int cantidad = m.path("duracionCantidad").asInt(0);
            plan.setDuracionUnidad(plan.isPagoUnico() ? null : unidad);
            plan.setDuracionCantidad(plan.isPagoUnico() || cantidad < 1 ? null : cantidad);
            List<String> beneficios = new ArrayList<>();
            m.path("beneficios").forEach(b -> beneficios.add(b.asText()));
            plan.setBeneficios(beneficios);
            plan.setDestacado(m.path("destacado").asBoolean(false));
            x.setPlan(plan);
        }

        List<JsonNode> ordenadas = new ArrayList<>();
        p.path("variants").forEach(ordenadas::add);
        ordenadas.sort(Comparator.comparingInt((JsonNode v) -> v.path("variant_rank").asInt(0))
                .thenComparing(v -> v.path("created_at").asText("")));
        List<Producto.Variante> lista = new ArrayList<>();
        for (int i = 0; i < ordenadas.size(); i++) {
            JsonNode v = ordenadas.get(i);
            Producto.Variante y = new Producto.Variante();
            y.setId(v.path("id").asText());
            y.setPresentacion(v.path("metadata").path("presentacion").asText(v.path("title").asText()));
            y.setSabor(texto(v.path("metadata").path("sabor")));
            y.setPrecio(precio(v));
            y.setOrden(i);
            boolean controlar = !x.esPlan() && v.path("manage_inventory").asBoolean(false);
            y.setControlarInventario(controlar);
            if (controlar) {
                int existencias = 0;
                int reservadas = 0;
                for (JsonNode nivel : v.path("inventory_items").path(0).path("inventory").path("location_levels")) {
                    existencias += nivel.path("stocked_quantity").asInt(0);
                    reservadas += nivel.path("reserved_quantity").asInt(0);
                }
                y.setExistencias(existencias);
                y.setApartadas(reservadas);
                y.setDisponible(existencias - reservadas);
            }
            lista.add(y);
        }
        x.setVariantes(lista);
        return x;
    }

    // ═══════════════════════════ AYUDANTES ═══════════════════════════

    // Canal de venta que el gimnasio tenía en Medusa (queda en su documento
    // aunque Spring ya no lo use): sirve para reconocer lo que no trae gymId.
    private String canalDeVenta(String gymId) {
        Document gym = mongo.getCollection("gyms").find(new Document("_id", new org.bson.types.ObjectId(gymId))).first();
        Object tienda = gym == null ? null : gym.get("tienda");
        return tienda instanceof Document t ? t.getString("salesChannelId") : null;
    }

    private static boolean delGimnasio(JsonNode metadata, JsonNode canales, String canalDelPedido, String gymId, String canal) {
        if (gymId.equals(metadata.path("gymId").asText())) return true;
        if (canal == null) return false;
        if (canal.equals(canalDelPedido)) return true;
        if (canales != null) {
            for (JsonNode c : canales) if (canal.equals(c.path("id").asText())) return true;
        }
        return false;
    }

    private static double precio(JsonNode variante) {
        for (JsonNode p : variante.path("prices")) {
            if (TiendaGymService.MONEDA.equals(p.path("currency_code").asText())) return p.path("amount").asDouble();
        }
        return 0;
    }

    private static String texto(JsonNode n) {
        return n.isMissingNode() || n.isNull() || n.asText().isBlank() ? null : n.asText();
    }

    private static Instant instante(JsonNode n) {
        String t = texto(n);
        if (t == null) return null;
        try {
            return Instant.parse(t);
        } catch (Exception e) {
            return null;
        }
    }

    // Lector mínimo de la Admin API de Medusa, solo para esta migración: usa la
    // llave que llegó en la petición y no la guarda en ningún lado.
    private final class Medusa {
        private final RestClient http;
        private final String base;
        private final String autorizacion;

        Medusa(String url, String llave) {
            if (url == null || url.isBlank() || llave == null || llave.isBlank()) {
                throw new TiendaException(HttpStatus.BAD_REQUEST, "Manda la URL y la llave de admin de Medusa.");
            }
            this.base = url.trim().replaceAll("/+$", "");
            this.autorizacion = "Basic " + Base64.getEncoder().encodeToString((llave.trim() + ":").getBytes(StandardCharsets.UTF_8));
            SimpleClientHttpRequestFactory fabrica = new SimpleClientHttpRequestFactory();
            fabrica.setConnectTimeout(10_000);
            fabrica.setReadTimeout(120_000);
            this.http = RestClient.builder().requestFactory(fabrica).build();
        }

        JsonNode get(String ruta) {
            try {
                String r = http.get().uri(URI.create(base + ruta))
                        .header("Authorization", autorizacion)
                        .accept(MediaType.APPLICATION_JSON)
                        .retrieve().body(String.class);
                return json.readTree(r);
            } catch (RestClientResponseException e) {
                int codigo = e.getStatusCode().value();
                if (codigo == 401 || codigo == 403) {
                    throw new TiendaException(HttpStatus.BAD_REQUEST, "Medusa rechazó la llave de admin.");
                }
                throw new TiendaException(HttpStatus.BAD_GATEWAY, "Medusa respondió " + codigo + " al leer " + ruta.split("\\?")[0] + ".");
            } catch (TiendaException e) {
                throw e;
            } catch (Exception e) {
                throw new TiendaException(HttpStatus.BAD_GATEWAY, "No se pudo leer Medusa en " + base + ": " + e.getMessage());
            }
        }

        // Todas las páginas de una lista de la Admin API.
        List<JsonNode> todos(String ruta, String campos, String llave) {
            List<JsonNode> lista = new ArrayList<>();
            for (int offset = 0; ; offset += POR_PAGINA) {
                JsonNode r = get(ruta + "?fields=" + URLEncoder.encode(campos, StandardCharsets.UTF_8)
                        + "&limit=" + POR_PAGINA + "&offset=" + offset);
                r.path(llave).forEach(lista::add);
                if (r.path(llave).size() < POR_PAGINA || lista.size() >= r.path("count").asInt(lista.size())) break;
            }
            return lista;
        }
    }

}
