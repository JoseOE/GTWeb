package com.gymtrack.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.gymtrack.model.Gym;
import com.gymtrack.model.TiendaGym;
import com.gymtrack.repository.GymRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static com.gymtrack.service.MedusaClient.q;

// Prepara la tienda de cada gimnasio dentro de Medusa la primera vez que la
// usa (al dar de alta su primer producto, al abrir su tienda...). Nadie tiene
// que correr nada a mano por cada gimnasio nuevo.
//
// Por gimnasio se crean: canal de venta, almacén, "Recoger en el gimnasio"
// ($0) y una llave publicable ligada solo a ese canal. Cada pieza se guarda en
// Gym.tienda en cuanto existe, así que si Medusa falla a la mitad, el siguiente
// intento sigue desde ahí.
//
// Lo que comparten todos (región México, tipos y categorías) lo crea el seed de
// medusa/ y aquí solo se busca una vez y se guarda en memoria.
@Service
public class TiendaGymService {

    private static final Logger log = LoggerFactory.getLogger(TiendaGymService.class);
    public static final String MONEDA = "mxn";
    public static final String TIPO_PRODUCTO = "producto";
    public static final String TIPO_MEMBRESIA = "membresia";
    public static final String CATEGORIA_MEMBRESIAS = "membresias";

    private final MedusaClient medusa;
    private final GymRepository gymRepository;
    // Un candado por gimnasio: dos peticiones simultáneas no deben crear dos canales.
    private final Map<String, Object> candados = new ConcurrentHashMap<>();
    private volatile Base base;

    public TiendaGymService(MedusaClient medusa, GymRepository gymRepository) {
        this.medusa = medusa;
        this.gymRepository = gymRepository;
    }

    // Lo que es igual para todos los gimnasios.
    public record Base(String regionId, String perfilDeEnvioId, Map<String, String> tipos,
                       Map<String, Categoria> categorias) {
        public String tipoId(String valor) { return tipos.get(valor); }
    }

    public record Categoria(String id, String handle, String nombre) {}

    public Base base() {
        Base actual = base;
        if (actual != null) return actual;
        synchronized (this) {
            if (base != null) return base;

            JsonNode regiones = medusa.adminGet("/admin/regions" + q("fields", "id,name,currency_code", "currency_code", MONEDA)).path("regions");
            JsonNode perfiles = medusa.adminGet("/admin/shipping-profiles" + q("fields", "id", "type", "default")).path("shipping_profiles");
            JsonNode tipos = medusa.adminGet("/admin/product-types" + q("fields", "id,value", "value[]", List.of(TIPO_PRODUCTO, TIPO_MEMBRESIA))).path("product_types");
            JsonNode categorias = medusa.adminGet("/admin/product-categories" + q("fields", "id,handle,name", "limit", 100)).path("product_categories");

            if (regiones.isEmpty() || perfiles.isEmpty() || tipos.size() < 2) {
                throw new TiendaException(HttpStatus.SERVICE_UNAVAILABLE,
                        "La tienda todavía no tiene su configuración base. Corre \"npm run seed\" en la carpeta medusa/.");
            }
            Map<String, String> mapaTipos = new LinkedHashMap<>();
            tipos.forEach(t -> mapaTipos.put(t.path("value").asText(), t.path("id").asText()));
            Map<String, Categoria> mapaCategorias = new LinkedHashMap<>();
            categorias.forEach(c -> mapaCategorias.put(c.path("handle").asText(),
                    new Categoria(c.path("id").asText(), c.path("handle").asText(), c.path("name").asText())));

            base = new Base(regiones.get(0).path("id").asText(), perfiles.get(0).path("id").asText(),
                    mapaTipos, mapaCategorias);
            return base;
        }
    }

    // Devuelve la tienda del gimnasio, creándola en Medusa si falta algo.
    public TiendaGym asegurar(String gymId) {
        Gym gym = gymRepository.findById(gymId)
                .orElseThrow(() -> new TiendaException(HttpStatus.NOT_FOUND, "Gimnasio no encontrado."));
        if (gym.getTienda() != null && gym.getTienda().estaCompleta()) return gym.getTienda();

        synchronized (candados.computeIfAbsent(gymId, id -> new Object())) {
            // Otra petición pudo terminarla mientras esta esperaba el candado.
            gym = gymRepository.findById(gymId).orElseThrow();
            if (gym.getTienda() != null && gym.getTienda().estaCompleta()) return gym.getTienda();
            return inicializar(gym);
        }
    }

    private TiendaGym inicializar(Gym gym) {
        Base base = base();
        TiendaGym t = gym.getTienda() != null ? gym.getTienda() : new TiendaGym();
        gym.setTienda(t);
        String gymId = gym.getId();
        String nombre = gym.getNombre() == null || gym.getNombre().isBlank() ? "Gimnasio " + gymId : gym.getNombre();
        log.info("Preparando la tienda del gimnasio {} ({}) en Medusa…", nombre, gymId);

        // 1. Canal de venta: sus productos y pedidos no se mezclan con los de otros gimnasios.
        if (t.getSalesChannelId() == null) {
            JsonNode canal = medusa.adminPost("/admin/sales-channels" + q("fields", "id"), Map.of(
                    "name", nombre + " · " + gymId,
                    "description", "Tienda de " + nombre + " en GymTrack",
                    "metadata", Map.of("gymId", gymId)));
            t.setSalesChannelId(canal.path("sales_channel").path("id").asText());
            gymRepository.save(gym);
        }

        // 2. Almacén del gimnasio, ligado a su canal y al proveedor de entregas manual.
        if (t.getStockLocationId() == null) {
            String direccion = gym.getDireccion() == null || gym.getDireccion().isBlank() ? nombre : gym.getDireccion();
            JsonNode almacen = medusa.adminPost("/admin/stock-locations" + q("fields", "id"), Map.of(
                    "name", nombre + " · " + gymId,
                    "address", Map.of("address_1", direccion, "country_code", "MX"),
                    "metadata", Map.of("gymId", gymId)));
            String almacenId = almacen.path("stock_location").path("id").asText();
            medusa.adminPost("/admin/stock-locations/" + almacenId + "/sales-channels" + q("fields", "id"),
                    Map.of("add", List.of(t.getSalesChannelId())));
            medusa.adminPost("/admin/stock-locations/" + almacenId + "/fulfillment-providers" + q("fields", "id"),
                    Map.of("add", List.of("manual_manual")));
            t.setStockLocationId(almacenId);
            gymRepository.save(gym);
        }

        // 3. "Recoger en el gimnasio": conjunto de entrega tipo pickup con su zona (México).
        if (t.getFulfillmentSetId() == null) {
            String nombreConjunto = "Recoger en el gimnasio · " + gymId;
            JsonNode respuesta = medusa.adminPost("/admin/stock-locations/" + t.getStockLocationId() + "/fulfillment-sets"
                            + q("fields", "id,fulfillment_sets.id,fulfillment_sets.name"),
                    Map.of("name", nombreConjunto, "type", "pickup"));
            t.setFulfillmentSetId(buscarPorNombre(respuesta.path("stock_location").path("fulfillment_sets"), nombreConjunto));
            gymRepository.save(gym);
        }
        if (t.getServiceZoneId() == null) {
            String nombreZona = "México · " + gymId;
            JsonNode respuesta = medusa.adminPost("/admin/fulfillment-sets/" + t.getFulfillmentSetId() + "/service-zones"
                            + q("fields", "id,service_zones.id,service_zones.name"),
                    Map.of("name", nombreZona, "geo_zones", List.of(Map.of("type", "country", "country_code", "mx"))));
            t.setServiceZoneId(buscarPorNombre(respuesta.path("fulfillment_set").path("service_zones"), nombreZona));
            gymRepository.save(gym);
        }
        if (t.getShippingOptionId() == null) {
            JsonNode opcion = medusa.adminPost("/admin/shipping-options" + q("fields", "id"), Map.of(
                    "name", "Recoger en el gimnasio",
                    "service_zone_id", t.getServiceZoneId(),
                    "shipping_profile_id", base.perfilDeEnvioId(),
                    "provider_id", "manual_manual",
                    "price_type", "flat",
                    "type", Map.of("label", "Recoger en el gimnasio",
                            "description", "Pasa por tu compra a la recepción de " + nombre, "code", "recoger"),
                    "prices", List.of(Map.of("currency_code", MONEDA, "amount", 0)),
                    "rules", List.of(
                            Map.of("attribute", "enabled_in_store", "value", "true", "operator", "eq"),
                            Map.of("attribute", "is_return", "value", "false", "operator", "eq"))));
            t.setShippingOptionId(opcion.path("shipping_option").path("id").asText());
            gymRepository.save(gym);
        }

        // 4. Llave publicable: la Store API solo ve el canal de este gimnasio.
        if (t.getPublishableKeyId() == null) {
            JsonNode llave = medusa.adminPost("/admin/api-keys", Map.of(
                    "title", "Tienda " + nombre + " · " + gymId, "type", "publishable")).path("api_key");
            String llaveId = llave.path("id").asText();
            medusa.adminPost("/admin/api-keys/" + llaveId + "/sales-channels",
                    Map.of("add", List.of(t.getSalesChannelId())));
            t.setPublishableKeyId(llaveId);
            t.setPublishableKey(llave.path("token").asText());
            gymRepository.save(gym);
        }

        t.setLista(Instant.now());
        gymRepository.save(gym);
        log.info("Tienda del gimnasio {} lista.", gymId);
        return t;
    }

    private String buscarPorNombre(JsonNode lista, String nombre) {
        for (JsonNode n : lista) {
            if (nombre.equals(n.path("name").asText())) return n.path("id").asText();
        }
        throw new TiendaException(HttpStatus.BAD_GATEWAY, "La tienda no devolvió \"" + nombre + "\". Intenta de nuevo.");
    }
}
