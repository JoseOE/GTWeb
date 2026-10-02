package com.gymtrack.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.gymtrack.model.Gym;
import com.gymtrack.model.TiendaGym;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.StreamSupport;

import static com.gymtrack.service.MedusaClient.q;
import static com.gymtrack.service.TiendaGymService.CATEGORIA_MEMBRESIAS;
import static com.gymtrack.service.TiendaGymService.MONEDA;
import static com.gymtrack.service.TiendaGymService.TIPO_MEMBRESIA;
import static com.gymtrack.service.TiendaGymService.TIPO_PRODUCTO;

// Lo que el dueño vende: productos (con variantes, precio y stock) y planes de
// membresía. Todo vive en Medusa; aquí se traduce entre el formato sencillo que
// usa el panel (y la app) y el de la Admin API.
//
// Cómo se guarda en Medusa:
//  - Producto de tipo "producto" o "membresia", en el canal de venta del gimnasio.
//  - Una sola opción, "Variante", cuyo valor es la etiqueta de cada variante
//    ("Bote 2 lb · Vainilla"). La presentación y el sabor van por separado en
//    el metadata de la variante para que la tienda pueda armar sus selectores.
//  - Precio en MXN con IVA incluido (la región México es tax-inclusive).
//  - Stock en el almacén del gimnasio. "No controlar inventario" (scoops,
//    planes) = manage_inventory false: nunca se agota.
@Service
public class CatalogoService {

    static final String OPCION = "Variante";
    private static final String VARIANTE_PLAN = "Plan";
    private static final int MAX_VARIANTES = 30;

    // Campos que se piden a Medusa para armar la vista de un producto o plan.
    private static final String CAMPOS = String.join(",",
            "id", "title", "description", "status", "thumbnail", "metadata", "type_id", "created_at",
            "categories.id", "categories.handle", "categories.name",
            "sales_channels.id",
            "options.id", "options.title", "options.values.id", "options.values.value",
            "variants.id", "variants.title", "variants.manage_inventory", "variants.metadata",
            "variants.created_at", "variants.prices.amount", "variants.prices.currency_code",
            "variants.inventory_items.inventory_item_id",
            "variants.inventory_items.inventory.location_levels.location_id",
            "variants.inventory_items.inventory.location_levels.stocked_quantity",
            "variants.inventory_items.inventory.location_levels.reserved_quantity",
            "variants.inventory_items.inventory.location_levels.available_quantity");

    private final MedusaClient medusa;
    private final TiendaGymService tiendas;

    public CatalogoService(MedusaClient medusa, TiendaGymService tiendas) {
        this.medusa = medusa;
        this.tiendas = tiendas;
    }

    // ═══════════════════════════ PRODUCTOS ═══════════════════════════

    public List<Map<String, Object>> listarProductos(String gymId) {
        TiendaGym t = tiendas.asegurar(gymId);
        return listar(gymId, TIPO_PRODUCTO).stream().map(p -> vistaProducto(p, t)).toList();
    }

    public Map<String, Object> obtenerProducto(String gymId, String productoId) {
        TiendaGym t = tiendas.asegurar(gymId);
        return vistaProducto(exigirDelGimnasio(productoId, t, TIPO_PRODUCTO), t);
    }

    public Map<String, Object> crearProducto(String gymId, ProductoRequest r) {
        List<VarianteLimpia> variantes = validar(r);
        TiendaGym t = tiendas.asegurar(gymId);
        TiendaGymService.Base base = tiendas.base();

        Map<String, Object> cuerpo = datosDelProducto(r.getNombre(), r.getDescripcion(), r.getImagen(),
                r.getActivo(), categoriaDeProducto(r.getCategoria(), base), Map.of("gymId", gymId));
        cuerpo.put("handle", handleUnico(r.getNombre()));
        cuerpo.put("type_id", base.tipoId(TIPO_PRODUCTO));
        cuerpo.put("sales_channels", List.of(Map.of("id", t.getSalesChannelId())));
        cuerpo.put("shipping_profile_id", base.perfilDeEnvioId());
        cuerpo.put("options", List.of(Map.of("title", OPCION, "values", variantes.stream().map(VarianteLimpia::etiqueta).toList())));
        cuerpo.put("variants", variantes.stream().map(v -> datosDeVariante(v, null)).toList());

        JsonNode creado = medusa.adminPost("/admin/products" + q("fields", "id"), cuerpo).path("product");
        String id = creado.path("id").asText();
        ajustarExistencias(producto(id), variantes, t.getStockLocationId());
        return vistaProducto(producto(id), t);
    }

    public Map<String, Object> actualizarProducto(String gymId, String productoId, ProductoRequest r) {
        List<VarianteLimpia> variantes = validar(r);
        TiendaGym t = tiendas.asegurar(gymId);
        TiendaGymService.Base base = tiendas.base();
        JsonNode actual = exigirDelGimnasio(productoId, t, TIPO_PRODUCTO);

        // 1. Las etiquetas nuevas se agregan como valores de la opción "Variante".
        JsonNode opcion = actual.path("options").path(0);
        Set<String> valoresExistentes = new HashSet<>();
        opcion.path("values").forEach(v -> valoresExistentes.add(v.path("value").asText()));
        List<Map<String, String>> nuevos = variantes.stream()
                .map(VarianteLimpia::etiqueta)
                .filter(e -> !valoresExistentes.contains(e))
                .map(e -> Map.of("value", e))
                .toList();
        if (!nuevos.isEmpty()) {
            medusa.adminPost("/admin/products/" + productoId + "/options/batch" + q("fields", "id"),
                    Map.of("update", List.of(Map.of("product_option_id", opcion.path("id").asText(), "add", nuevos))));
        }

        // 2. Variantes: las que traen id se actualizan, las nuevas se crean y las
        //    que ya no vienen se borran (los pedidos viejos conservan su copia).
        Set<String> idsExistentes = new HashSet<>();
        actual.path("variants").forEach(v -> idsExistentes.add(v.path("id").asText()));
        List<Map<String, Object>> crear = new ArrayList<>();
        List<Map<String, Object>> actualizar = new ArrayList<>();
        Set<String> conservadas = new HashSet<>();
        for (VarianteLimpia v : variantes) {
            if (v.id() != null && idsExistentes.contains(v.id())) {
                actualizar.add(datosDeVariante(v, v.id()));
                conservadas.add(v.id());
            } else {
                crear.add(datosDeVariante(v, null));
            }
        }
        List<String> borrar = idsExistentes.stream().filter(id -> !conservadas.contains(id)).toList();
        medusa.adminPost("/admin/products/" + productoId + "/variants/batch",
                Map.of("create", crear, "update", actualizar, "delete", borrar));

        // 3. Datos generales del producto.
        medusa.adminPost("/admin/products/" + productoId + q("fields", "id"),
                datosDelProducto(r.getNombre(), r.getDescripcion(), r.getImagen(), r.getActivo(),
                        categoriaDeProducto(r.getCategoria(), base), Map.of("gymId", gymId)));

        // 4. Existencias en el almacén del gimnasio.
        ajustarExistencias(producto(productoId), variantes, t.getStockLocationId());
        return vistaProducto(producto(productoId), t);
    }

    public void eliminarProducto(String gymId, String productoId) {
        TiendaGym t = tiendas.asegurar(gymId);
        exigirDelGimnasio(productoId, t, TIPO_PRODUCTO);
        medusa.adminDelete("/admin/products/" + productoId);
    }

    // ═══════════════════════════ PLANES ═══════════════════════════

    public List<Map<String, Object>> listarPlanes(String gymId) {
        return listar(gymId, TIPO_MEMBRESIA).stream().map(this::vistaPlan).toList();
    }

    public Map<String, Object> crearPlan(String gymId, PlanRequest r) {
        validar(r);
        TiendaGym t = tiendas.asegurar(gymId);
        TiendaGymService.Base base = tiendas.base();

        Map<String, Object> cuerpo = datosDelProducto(r.getNombre(), r.getDescripcion(), null, r.getActivo(),
                base.categorias().get(CATEGORIA_MEMBRESIAS), metadataDelPlan(gymId, r));
        cuerpo.put("handle", handleUnico(r.getNombre()));
        cuerpo.put("type_id", base.tipoId(TIPO_MEMBRESIA));
        cuerpo.put("sales_channels", List.of(Map.of("id", t.getSalesChannelId())));
        cuerpo.put("shipping_profile_id", base.perfilDeEnvioId());
        cuerpo.put("options", List.of(Map.of("title", OPCION, "values", List.of(VARIANTE_PLAN))));
        cuerpo.put("variants", List.of(Map.of(
                "title", VARIANTE_PLAN,
                "manage_inventory", false,
                "options", Map.of(OPCION, VARIANTE_PLAN),
                "prices", List.of(Map.of("currency_code", MONEDA, "amount", r.getPrecio())))));

        String id = medusa.adminPost("/admin/products" + q("fields", "id"), cuerpo).path("product").path("id").asText();
        return vistaPlan(producto(id));
    }

    public Map<String, Object> actualizarPlan(String gymId, String planId, PlanRequest r) {
        validar(r);
        TiendaGym t = tiendas.asegurar(gymId);
        TiendaGymService.Base base = tiendas.base();
        JsonNode actual = exigirDelGimnasio(planId, t, TIPO_MEMBRESIA);

        medusa.adminPost("/admin/products/" + planId + q("fields", "id"),
                datosDelProducto(r.getNombre(), r.getDescripcion(), null, r.getActivo(),
                        base.categorias().get(CATEGORIA_MEMBRESIAS), metadataDelPlan(gymId, r)));
        String varianteId = actual.path("variants").path(0).path("id").asText();
        medusa.adminPost("/admin/products/" + planId + "/variants/" + varianteId + q("fields", "id"),
                Map.of("prices", List.of(Map.of("currency_code", MONEDA, "amount", r.getPrecio()))));
        return vistaPlan(producto(planId));
    }

    public void eliminarPlan(String gymId, String planId) {
        TiendaGym t = tiendas.asegurar(gymId);
        exigirDelGimnasio(planId, t, TIPO_MEMBRESIA);
        medusa.adminDelete("/admin/products/" + planId);
    }

    // Plan con el que se registra un pago a mano desde el panel.
    public PlanPagado planParaPago(String gymId, String planId) {
        TiendaGym t = tiendas.asegurar(gymId);
        return leerPlan(exigirDelGimnasio(planId, t, TIPO_MEMBRESIA))
                .orElseThrow(() -> new TiendaException(HttpStatus.BAD_REQUEST,
                        "Ese concepto es de pago único: no extiende la membresía."));
    }

    // Duración de un producto "membresia" ya leído de Medusa. Vacío si es de
    // pago único (inscripción) o si no tiene una duración válida.
    public static Optional<PlanPagado> leerPlan(JsonNode producto) {
        JsonNode m = producto.path("metadata");
        String unidad = m.path("duracionUnidad").asText("");
        int cantidad = m.path("duracionCantidad").asInt(0);
        if (m.path("pagoUnico").asBoolean(false) || !PlanPagado.UNIDADES.contains(unidad) || cantidad < 1) {
            return Optional.empty();
        }
        return Optional.of(new PlanPagado(producto.path("id").asText(), producto.path("title").asText(), unidad, cantidad));
    }

    // Planes sugeridos, con el precio calculado desde la cuota mensual del
    // gimnasio. El dueño los edita antes de guardarlos.
    public List<Map<String, Object>> presets(Gym gym) {
        Double cuota = gym.getCuotaMensual();
        List<Map<String, Object>> lista = new ArrayList<>();
        lista.add(preset("Visita", "dia", 1, precioSugerido(cuota, 0.10), false, false,
                List.of("Acceso por un día", "Uso de todas las áreas")));
        lista.add(preset("Semana", "semana", 1, precioSugerido(cuota, 0.35), false, false,
                List.of("7 días de acceso", "Ideal para probar el gimnasio")));
        lista.add(preset("Mensual", "mes", 1, precioSugerido(cuota, 1), true, false,
                List.of("Acceso ilimitado todo el mes", "Rutinas en la app GymTrack")));
        lista.add(preset("Trimestral", "mes", 3, precioSugerido(cuota, 3 * 0.90), false, false,
                List.of("3 meses de acceso", "Ahorras 10 % contra el mensual", "Rutinas en la app GymTrack")));
        lista.add(preset("Semestral", "mes", 6, precioSugerido(cuota, 6 * 0.85), false, false,
                List.of("6 meses de acceso", "Ahorras 15 % contra el mensual", "Rutinas en la app GymTrack")));
        lista.add(preset("Anual", "mes", 12, precioSugerido(cuota, 12 * 0.80), false, false,
                List.of("12 meses de acceso", "Ahorras 20 % contra el mensual", "Rutinas en la app GymTrack")));
        lista.add(preset("Inscripción", null, null, precioSugerido(cuota, 0.5), false, true,
                List.of("Pago único al registrarte", "Credencial de acceso")));
        return lista;
    }

    // ═══════════════════════════ VISTAS ═══════════════════════════

    // Forma en que el panel (y la app) reciben un producto.
    Map<String, Object> vistaProducto(JsonNode p, TiendaGym t) {
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("id", p.path("id").asText());
        v.put("nombre", p.path("title").asText());
        v.put("descripcion", texto(p.path("description")));
        v.put("imagen", texto(p.path("thumbnail")));
        v.put("activo", "published".equals(p.path("status").asText()));
        JsonNode categoria = p.path("categories").path(0);
        v.put("categoria", categoria.isMissingNode() ? null : Map.of(
                "handle", categoria.path("handle").asText(), "nombre", categoria.path("name").asText()));

        List<Map<String, Object>> variantes = new ArrayList<>();
        Double desde = null;
        for (JsonNode var : ordenadas(p.path("variants"))) {
            Map<String, Object> x = new LinkedHashMap<>();
            Double precio = precio(var);
            boolean controlar = var.path("manage_inventory").asBoolean(false);
            x.put("id", var.path("id").asText());
            x.put("nombre", var.path("title").asText());
            x.put("presentacion", var.path("metadata").path("presentacion").asText(var.path("title").asText()));
            x.put("sabor", texto(var.path("metadata").path("sabor")));
            x.put("precio", precio);
            x.put("controlarInventario", controlar);
            JsonNode nivel = nivelEnAlmacen(var, t.getStockLocationId());
            x.put("existencias", controlar ? nivel.path("stocked_quantity").asInt(0) : null);
            x.put("disponible", controlar ? nivel.path("available_quantity").asInt(0) : null);
            variantes.add(x);
            if (precio != null && (desde == null || precio < desde)) desde = precio;
        }
        v.put("variantes", variantes);
        v.put("precioDesde", desde);
        return v;
    }

    Map<String, Object> vistaPlan(JsonNode p) {
        JsonNode m = p.path("metadata");
        JsonNode variante = p.path("variants").path(0);
        Optional<PlanPagado> plan = leerPlan(p);
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("id", p.path("id").asText());
        v.put("varianteId", variante.path("id").asText(null));
        v.put("nombre", p.path("title").asText());
        v.put("descripcion", texto(p.path("description")));
        v.put("precio", precio(variante));
        v.put("pagoUnico", m.path("pagoUnico").asBoolean(false));
        v.put("duracionUnidad", plan.map(PlanPagado::unidad).orElse(null));
        v.put("duracionCantidad", plan.map(PlanPagado::cantidad).orElse(null));
        v.put("duracionTexto", plan.map(CatalogoService::duracionTexto).orElse("Pago único"));
        List<String> beneficios = new ArrayList<>();
        m.path("beneficios").forEach(b -> beneficios.add(b.asText()));
        v.put("beneficios", beneficios);
        v.put("destacado", m.path("destacado").asBoolean(false));
        v.put("activo", "published".equals(p.path("status").asText()));
        return v;
    }

    // "1 día", "7 días", "1 semana", "3 meses"...
    public static String duracionTexto(PlanPagado p) {
        int n = p.cantidad();
        return switch (p.unidad()) {
            case "dia" -> n + (n == 1 ? " día" : " días");
            case "semana" -> n + (n == 1 ? " semana" : " semanas");
            default -> n + (n == 1 ? " mes" : " meses");
        };
    }

    // ═══════════════════════════ AYUDANTES ═══════════════════════════

    private List<JsonNode> listar(String gymId, String tipo) {
        TiendaGym t = tiendas.asegurar(gymId);
        JsonNode lista = medusa.adminGet("/admin/products" + q(
                "fields", CAMPOS,
                "sales_channel_id[]", List.of(t.getSalesChannelId()),
                "type_id[]", List.of(tiendas.base().tipoId(tipo)),
                "order", "title",
                "limit", 500)).path("products");
        return StreamSupport.stream(lista.spliterator(), false).toList();
    }

    JsonNode producto(String id) {
        return medusa.adminGet("/admin/products/" + id + q("fields", CAMPOS)).path("product");
    }

    // Un producto de otro gimnasio responde igual que uno que no existe.
    private JsonNode exigirDelGimnasio(String productoId, TiendaGym t, String tipo) {
        JsonNode p;
        try {
            p = producto(productoId);
        } catch (TiendaException e) {
            if (e.getStatus() == HttpStatus.NOT_FOUND) throw noEncontrado(tipo);
            throw e;
        }
        boolean delGimnasio = StreamSupport.stream(p.path("sales_channels").spliterator(), false)
                .anyMatch(sc -> t.getSalesChannelId().equals(sc.path("id").asText()));
        if (!delGimnasio || !tiendas.base().tipoId(tipo).equals(p.path("type_id").asText())) {
            throw noEncontrado(tipo);
        }
        return p;
    }

    private TiendaException noEncontrado(String tipo) {
        return new TiendaException(HttpStatus.NOT_FOUND,
                TIPO_MEMBRESIA.equals(tipo) ? "Plan no encontrado en tu gimnasio." : "Producto no encontrado en tu gimnasio.");
    }

    private Map<String, Object> datosDelProducto(String nombre, String descripcion, String imagen, Boolean activo,
                                                 TiendaGymService.Categoria categoria, Map<String, Object> metadata) {
        Map<String, Object> d = new HashMap<>();
        d.put("title", nombre.trim());
        d.put("description", descripcion == null ? "" : descripcion.trim());
        d.put("status", Boolean.FALSE.equals(activo) ? "draft" : "published");
        d.put("categories", List.of(Map.of("id", categoria.id())));
        d.put("metadata", metadata);
        boolean conImagen = imagen != null && !imagen.isBlank();
        d.put("thumbnail", conImagen ? imagen : null);
        d.put("images", conImagen ? List.of(Map.of("url", imagen)) : List.of());
        return d;
    }

    // El handle es único en todo Medusa, no por canal: sin el sufijo, el segundo
    // gimnasio que creara un plan "Mensual" chocaría con el del primero.
    private static String handleUnico(String nombre) {
        String base = Normalizer.normalize(nombre.trim().toLowerCase(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
        if (base.length() > 60) base = base.substring(0, 60);
        return (base.isEmpty() ? "producto" : base) + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private Map<String, Object> datosDeVariante(VarianteLimpia v, String id) {
        Map<String, Object> d = new HashMap<>();
        if (id != null) d.put("id", id);
        d.put("title", v.etiqueta());
        d.put("manage_inventory", v.controlar());
        d.put("options", Map.of(OPCION, v.etiqueta()));
        d.put("prices", List.of(Map.of("currency_code", MONEDA, "amount", v.precio())));
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("presentacion", v.presentacion());
        metadata.put("sabor", v.sabor() == null ? "" : v.sabor());
        d.put("metadata", metadata);
        return d;
    }

    private Map<String, Object> metadataDelPlan(String gymId, PlanRequest r) {
        boolean pagoUnico = Boolean.TRUE.equals(r.getPagoUnico());
        Map<String, Object> m = new HashMap<>();
        m.put("gymId", gymId);
        m.put("pagoUnico", pagoUnico);
        // Se mandan siempre todas las llaves para que un plan que pasa a ser de
        // pago único no conserve su duración anterior.
        m.put("duracionUnidad", pagoUnico ? "" : r.getDuracionUnidad());
        m.put("duracionCantidad", pagoUnico ? 0 : r.getDuracionCantidad());
        m.put("beneficios", limpiarBeneficios(r.getBeneficios()));
        m.put("destacado", Boolean.TRUE.equals(r.getDestacado()));
        return m;
    }

    // Fija las existencias de cada variante con inventario en el almacén del gimnasio.
    private void ajustarExistencias(JsonNode producto, List<VarianteLimpia> pedidas, String almacenId) {
        Map<String, VarianteLimpia> porEtiqueta = new HashMap<>();
        pedidas.forEach(v -> porEtiqueta.put(v.etiqueta(), v));
        List<Map<String, Object>> crear = new ArrayList<>();
        List<Map<String, Object>> actualizar = new ArrayList<>();

        for (JsonNode var : producto.path("variants")) {
            VarianteLimpia pedida = porEtiqueta.get(var.path("title").asText());
            if (pedida == null || !pedida.controlar()) continue;
            String inventarioId = var.path("inventory_items").path(0).path("inventory_item_id").asText(null);
            if (inventarioId == null) {
                // Variante que antes no controlaba inventario: Medusa no le creó uno.
                inventarioId = crearInventario(producto.path("id").asText(), var);
            }
            Map<String, Object> nivel = Map.of("inventory_item_id", inventarioId, "location_id", almacenId,
                    "stocked_quantity", pedida.existencias());
            if (nivelEnAlmacen(var, almacenId).isMissingNode()) crear.add(nivel);
            else actualizar.add(nivel);
        }
        if (!crear.isEmpty() || !actualizar.isEmpty()) {
            medusa.adminPost("/admin/inventory-items/location-levels/batch", Map.of("create", crear, "update", actualizar));
        }
    }

    private String crearInventario(String productoId, JsonNode variante) {
        String id = medusa.adminPost("/admin/inventory-items" + q("fields", "id"),
                Map.of("title", variante.path("title").asText())).path("inventory_item").path("id").asText();
        medusa.adminPost("/admin/products/" + productoId + "/variants/" + variante.path("id").asText() + "/inventory-items"
                + q("fields", "id"), Map.of("inventory_item_id", id, "required_quantity", 1));
        return id;
    }

    private static JsonNode nivelEnAlmacen(JsonNode variante, String almacenId) {
        for (JsonNode nivel : variante.path("inventory_items").path(0).path("inventory").path("location_levels")) {
            if (almacenId.equals(nivel.path("location_id").asText())) return nivel;
        }
        return com.fasterxml.jackson.databind.node.MissingNode.getInstance();
    }

    private static Double precio(JsonNode variante) {
        for (JsonNode p : variante.path("prices")) {
            if (MONEDA.equals(p.path("currency_code").asText())) return p.path("amount").asDouble();
        }
        return null;
    }

    private static List<JsonNode> ordenadas(JsonNode variantes) {
        return StreamSupport.stream(variantes.spliterator(), false)
                .sorted(Comparator.comparing(v -> v.path("created_at").asText("")))
                .toList();
    }

    private static String texto(JsonNode n) {
        return n.isMissingNode() || n.isNull() || n.asText().isBlank() ? null : n.asText();
    }

    private TiendaGymService.Categoria categoriaDeProducto(String handle, TiendaGymService.Base base) {
        TiendaGymService.Categoria c = handle == null ? null : base.categorias().get(handle);
        if (c == null || CATEGORIA_MEMBRESIAS.equals(handle)) {
            throw new TiendaException(HttpStatus.BAD_REQUEST, "Elige la categoría del producto.");
        }
        return c;
    }

    private static Map<String, Object> preset(String nombre, String unidad, Integer cantidad, Double precio,
                                              boolean destacado, boolean pagoUnico, List<String> beneficios) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("nombre", nombre);
        p.put("duracionUnidad", unidad);
        p.put("duracionCantidad", cantidad);
        p.put("precio", precio);
        p.put("destacado", destacado);
        p.put("pagoUnico", pagoUnico);
        p.put("beneficios", beneficios);
        return p;
    }

    // Redondea a múltiplos de $5 para que el precio sugerido se vea "de mostrador".
    private static Double precioSugerido(Double cuota, double factor) {
        if (cuota == null || cuota <= 0) return null;
        return Math.round(cuota * factor / 5.0) * 5.0;
    }

    private static List<String> limpiarBeneficios(List<String> beneficios) {
        if (beneficios == null) return List.of();
        return beneficios.stream().filter(b -> b != null && !b.isBlank()).map(String::trim).limit(10).toList();
    }

    // ─── Validación ───

    record VarianteLimpia(String id, String presentacion, String sabor, double precio,
                          boolean controlar, int existencias) {
        String etiqueta() {
            return sabor == null ? presentacion : presentacion + " · " + sabor;
        }
    }

    private List<VarianteLimpia> validar(ProductoRequest r) {
        if (r == null || r.getNombre() == null || r.getNombre().isBlank()) {
            throw new TiendaException(HttpStatus.BAD_REQUEST, "El producto necesita un nombre.");
        }
        validarImagen(r.getImagen());
        if (r.getVariantes() == null || r.getVariantes().isEmpty()) {
            throw new TiendaException(HttpStatus.BAD_REQUEST, "Agrega al menos una presentación con su precio.");
        }
        if (r.getVariantes().size() > MAX_VARIANTES) {
            throw new TiendaException(HttpStatus.BAD_REQUEST, "Un producto puede tener hasta " + MAX_VARIANTES + " variantes.");
        }
        List<VarianteLimpia> limpias = new ArrayList<>();
        Set<String> etiquetas = new HashSet<>();
        for (VarianteRequest v : r.getVariantes()) {
            String presentacion = v.getPresentacion() == null || v.getPresentacion().isBlank() ? "Única" : v.getPresentacion().trim();
            String sabor = v.getSabor() == null || v.getSabor().isBlank() ? null : v.getSabor().trim();
            if (v.getPrecio() == null || v.getPrecio() <= 0) {
                throw new TiendaException(HttpStatus.BAD_REQUEST, "Ponle precio a \"" + presentacion + "\".");
            }
            boolean controlar = !Boolean.FALSE.equals(v.getControlarInventario());
            int existencias = v.getExistencias() == null ? 0 : v.getExistencias();
            if (controlar && existencias < 0) {
                throw new TiendaException(HttpStatus.BAD_REQUEST, "Las existencias no pueden ser negativas.");
            }
            VarianteLimpia limpia = new VarianteLimpia(v.getId(), presentacion, sabor,
                    Math.round(v.getPrecio() * 100) / 100.0, controlar, existencias);
            if (!etiquetas.add(limpia.etiqueta().toLowerCase())) {
                throw new TiendaException(HttpStatus.BAD_REQUEST, "La variante \"" + limpia.etiqueta() + "\" está repetida.");
            }
            limpias.add(limpia);
        }
        return limpias;
    }

    private void validar(PlanRequest r) {
        if (r == null || r.getNombre() == null || r.getNombre().isBlank()) {
            throw new TiendaException(HttpStatus.BAD_REQUEST, "El plan necesita un nombre.");
        }
        if (r.getPrecio() == null || r.getPrecio() <= 0) {
            throw new TiendaException(HttpStatus.BAD_REQUEST, "Ponle precio al plan.");
        }
        if (!Boolean.TRUE.equals(r.getPagoUnico())) {
            if (r.getDuracionUnidad() == null || !PlanPagado.UNIDADES.contains(r.getDuracionUnidad())) {
                throw new TiendaException(HttpStatus.BAD_REQUEST, "Elige si el plan dura días, semanas o meses.");
            }
            if (r.getDuracionCantidad() == null || r.getDuracionCantidad() < 1 || r.getDuracionCantidad() > 36) {
                throw new TiendaException(HttpStatus.BAD_REQUEST, "La duración del plan debe estar entre 1 y 36.");
            }
        }
    }

    private void validarImagen(String imagen) {
        if (imagen != null && !imagen.isBlank() && !imagen.startsWith("https://") && !imagen.startsWith("http://")) {
            throw new TiendaException(HttpStatus.BAD_REQUEST, "La imagen debe subirse desde el panel.");
        }
    }

    // ─── Lo que manda el panel ───

    public static class ProductoRequest {
        private String nombre;
        private String descripcion;
        // handle de la categoría: suplementos | bebidas | snacks | accesorios
        private String categoria;
        // URL que devolvió POST /api/gyms/{gymId}/imagenes
        private String imagen;
        private Boolean activo;
        private List<VarianteRequest> variantes;

        public String getNombre() { return nombre; }
        public void setNombre(String nombre) { this.nombre = nombre; }

        public String getDescripcion() { return descripcion; }
        public void setDescripcion(String descripcion) { this.descripcion = descripcion; }

        public String getCategoria() { return categoria; }
        public void setCategoria(String categoria) { this.categoria = categoria; }

        public String getImagen() { return imagen; }
        public void setImagen(String imagen) { this.imagen = imagen; }

        public Boolean getActivo() { return activo; }
        public void setActivo(Boolean activo) { this.activo = activo; }

        public List<VarianteRequest> getVariantes() { return variantes; }
        public void setVariantes(List<VarianteRequest> variantes) { this.variantes = variantes; }
    }

    public static class VarianteRequest {
        // null = variante nueva
        private String id;
        private String presentacion;
        private String sabor;
        private Double precio;
        // false = no controlar inventario (scoops): nunca se agota
        private Boolean controlarInventario;
        private Integer existencias;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }

        public String getPresentacion() { return presentacion; }
        public void setPresentacion(String presentacion) { this.presentacion = presentacion; }

        public String getSabor() { return sabor; }
        public void setSabor(String sabor) { this.sabor = sabor; }

        public Double getPrecio() { return precio; }
        public void setPrecio(Double precio) { this.precio = precio; }

        public Boolean getControlarInventario() { return controlarInventario; }
        public void setControlarInventario(Boolean controlarInventario) { this.controlarInventario = controlarInventario; }

        public Integer getExistencias() { return existencias; }
        public void setExistencias(Integer existencias) { this.existencias = existencias; }
    }

    public static class PlanRequest {
        private String nombre;
        private String descripcion;
        private Double precio;
        // dia | semana | mes (se ignora si es de pago único)
        private String duracionUnidad;
        private Integer duracionCantidad;
        private List<String> beneficios;
        private Boolean destacado;
        private Boolean activo;
        // true = inscripción u otro cobro que no extiende la membresía
        private Boolean pagoUnico;

        public String getNombre() { return nombre; }
        public void setNombre(String nombre) { this.nombre = nombre; }

        public String getDescripcion() { return descripcion; }
        public void setDescripcion(String descripcion) { this.descripcion = descripcion; }

        public Double getPrecio() { return precio; }
        public void setPrecio(Double precio) { this.precio = precio; }

        public String getDuracionUnidad() { return duracionUnidad; }
        public void setDuracionUnidad(String duracionUnidad) { this.duracionUnidad = duracionUnidad; }

        public Integer getDuracionCantidad() { return duracionCantidad; }
        public void setDuracionCantidad(Integer duracionCantidad) { this.duracionCantidad = duracionCantidad; }

        public List<String> getBeneficios() { return beneficios; }
        public void setBeneficios(List<String> beneficios) { this.beneficios = beneficios; }

        public Boolean getDestacado() { return destacado; }
        public void setDestacado(Boolean destacado) { this.destacado = destacado; }

        public Boolean getActivo() { return activo; }
        public void setActivo(Boolean activo) { this.activo = activo; }

        public Boolean getPagoUnico() { return pagoUnico; }
        public void setPagoUnico(Boolean pagoUnico) { this.pagoUnico = pagoUnico; }
    }
}
