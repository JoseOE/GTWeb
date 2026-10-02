package com.gymtrack.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.gymtrack.model.TiendaGym;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.StreamSupport;

import static com.gymtrack.service.MedusaClient.q;
import static com.gymtrack.service.TiendaGymService.TIPO_MEMBRESIA;
import static com.gymtrack.service.TiendaGymService.TIPO_PRODUCTO;

// Lo que ve el miembro en la tienda de su gimnasio: productos y planes
// publicados, con el precio vigente y el stock disponible en su gimnasio.
//
// A diferencia del catálogo del dueño (CatalogoService, Admin API), esto pasa
// por la Store API con la llave publicable del gimnasio: Medusa ya filtra lo
// oculto, lo borrado y lo que pertenece a otros gimnasios.
@Service
public class EscaparateService {

    // La Store API devuelve null si se piden campos sueltos de las categorías o
    // de las variantes (categories.handle, variants.metadata); con "*categories"
    // y "*variants" sí los trae completos.
    private static final String CAMPOS = String.join(",",
            "id", "title", "description", "thumbnail", "metadata", "type_id", "created_at",
            "*categories",
            "*variants",
            "variants.calculated_price.calculated_amount",
            "+variants.inventory_quantity");

    private final MedusaClient medusa;
    private final TiendaGymService tiendas;

    public EscaparateService(MedusaClient medusa, TiendaGymService tiendas) {
        this.medusa = medusa;
        this.tiendas = tiendas;
    }

    // GET /api/tienda/{gymId}/productos: todo lo publicado; los planes van
    // primero (destacados arriba) y luego los productos por nombre.
    public List<Map<String, Object>> productos(String gymId, String categoria, String busqueda) {
        String texto = normalizar(busqueda);
        return listado(gymId).stream()
                .map(this::vista)
                .filter(v -> categoria == null || categoria.isBlank() || categoria.equals(handleCategoria(v)))
                .filter(v -> texto.isEmpty() || coincide(v, texto))
                .sorted(Comparator
                        .comparing((Map<String, Object> v) -> TIPO_MEMBRESIA.equals(v.get("tipo")) ? 0 : 1)
                        .thenComparing(v -> esDestacado(v) ? 0 : 1)
                        .thenComparing(v -> String.valueOf(v.get("nombre")), String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    public Map<String, Object> producto(String gymId, String productoId) {
        TiendaGym t = tiendas.asegurar(gymId);
        try {
            JsonNode p = medusa.storeGet(t.getPublishableKey(), "/store/products/" + productoId + q(
                    "region_id", tiendas.base().regionId(), "fields", CAMPOS)).path("product");
            return vista(p);
        } catch (TiendaException e) {
            if (e.getStatus() == HttpStatus.NOT_FOUND) {
                throw new TiendaException(HttpStatus.NOT_FOUND, "Ese producto ya no está a la venta.");
            }
            throw e;
        }
    }

    // Producto publicado que contiene la variante, o vacío si ya no se vende.
    public Optional<JsonNode> productoDeVariante(List<JsonNode> listado, String varianteId) {
        return listado.stream().filter(p -> variante(p, varianteId) != null).findFirst();
    }

    public List<JsonNode> listado(String gymId) {
        TiendaGym t = tiendas.asegurar(gymId);
        JsonNode lista = medusa.storeGet(t.getPublishableKey(), "/store/products" + q(
                "region_id", tiendas.base().regionId(),
                "fields", CAMPOS,
                "order", "title",
                "limit", 500)).path("products");
        return StreamSupport.stream(lista.spliterator(), false).toList();
    }

    public static JsonNode variante(JsonNode producto, String varianteId) {
        for (JsonNode v : producto.path("variants")) {
            if (varianteId.equals(v.path("id").asText())) return v;
        }
        return null;
    }

    public String tipo(JsonNode producto) {
        String tipoId = producto.path("type_id").asText();
        return tiendas.base().tipos().entrySet().stream()
                .filter(e -> e.getValue().equals(tipoId))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(TIPO_PRODUCTO);
    }

    public static double precio(JsonNode variante) {
        return variante.path("calculated_price").path("calculated_amount").asDouble();
    }

    // null = no controla inventario (nunca se agota).
    public static Integer disponible(JsonNode variante) {
        return variante.path("manage_inventory").asBoolean(false) ? variante.path("inventory_quantity").asInt(0) : null;
    }

    Map<String, Object> vista(JsonNode p) {
        String tipo = tipo(p);
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("id", p.path("id").asText());
        v.put("nombre", p.path("title").asText());
        v.put("descripcion", texto(p.path("description")));
        v.put("imagen", texto(p.path("thumbnail")));
        v.put("tipo", tipo);
        JsonNode categoria = p.path("categories").path(0);
        v.put("categoria", categoria.isMissingNode() ? null : Map.of(
                "handle", categoria.path("handle").asText(), "nombre", categoria.path("name").asText()));

        List<Map<String, Object>> variantes = new ArrayList<>();
        Double desde = null;
        boolean todoAgotado = true;
        List<JsonNode> ordenadas = StreamSupport.stream(p.path("variants").spliterator(), false)
                .sorted(CatalogoService.EN_ORDEN)
                .toList();
        for (JsonNode var : ordenadas) {
            double precio = precio(var);
            Integer disponible = disponible(var);
            boolean agotado = disponible != null && disponible <= 0;
            Map<String, Object> x = new LinkedHashMap<>();
            x.put("id", var.path("id").asText());
            x.put("nombre", var.path("title").asText());
            x.put("presentacion", var.path("metadata").path("presentacion").asText(var.path("title").asText()));
            x.put("sabor", texto(var.path("metadata").path("sabor")));
            x.put("precio", precio);
            x.put("controlarInventario", disponible != null);
            x.put("disponible", disponible);
            x.put("agotado", agotado);
            variantes.add(x);
            if (desde == null || precio < desde) desde = precio;
            if (!agotado) todoAgotado = false;
        }
        v.put("variantes", variantes);
        v.put("precioDesde", desde);
        v.put("agotado", todoAgotado);

        Map<String, Object> plan = null;
        if (TIPO_MEMBRESIA.equals(tipo)) {
            JsonNode m = p.path("metadata");
            Optional<PlanPagado> duracion = CatalogoService.leerPlan(p);
            List<String> beneficios = new ArrayList<>();
            m.path("beneficios").forEach(b -> beneficios.add(b.asText()));
            plan = new LinkedHashMap<>();
            plan.put("pagoUnico", m.path("pagoUnico").asBoolean(false));
            plan.put("duracionUnidad", duracion.map(PlanPagado::unidad).orElse(null));
            plan.put("duracionCantidad", duracion.map(PlanPagado::cantidad).orElse(null));
            plan.put("duracionTexto", duracion.map(CatalogoService::duracionTexto).orElse("Pago único"));
            plan.put("beneficios", beneficios);
            plan.put("destacado", m.path("destacado").asBoolean(false));
        }
        v.put("plan", plan);
        return v;
    }

    @SuppressWarnings("unchecked")
    private static String handleCategoria(Map<String, Object> v) {
        Map<String, Object> c = (Map<String, Object>) v.get("categoria");
        return c == null ? null : String.valueOf(c.get("handle"));
    }

    @SuppressWarnings("unchecked")
    private static boolean esDestacado(Map<String, Object> v) {
        Map<String, Object> plan = (Map<String, Object>) v.get("plan");
        return plan != null && Boolean.TRUE.equals(plan.get("destacado"));
    }

    // Busca en el nombre, la descripción y las variantes, sin importar acentos ni mayúsculas.
    @SuppressWarnings("unchecked")
    private static boolean coincide(Map<String, Object> v, String texto) {
        StringBuilder todo = new StringBuilder(String.valueOf(v.get("nombre"))).append(' ')
                .append(v.get("descripcion") == null ? "" : v.get("descripcion"));
        ((List<Map<String, Object>>) v.get("variantes")).forEach(x -> todo.append(' ').append(x.get("nombre")));
        return normalizar(todo.toString()).contains(texto);
    }

    static String normalizar(String s) {
        if (s == null) return "";
        return Normalizer.normalize(s.trim().toLowerCase(), Normalizer.Form.NFD).replaceAll("\\p{M}", "");
    }

    private static String texto(JsonNode n) {
        return n.isMissingNode() || n.isNull() || n.asText().isBlank() ? null : n.asText();
    }
}
