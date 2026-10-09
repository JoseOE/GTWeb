package com.gymtrack.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gymtrack.model.Pedido;
import com.gymtrack.model.Producto;
import com.gymtrack.repository.PaymentRepository;
import com.gymtrack.repository.PedidoRepository;
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
import java.util.Set;

import static com.gymtrack.model.Producto.TIPO_MEMBRESIA;
import static com.gymtrack.model.Producto.TIPO_PRODUCTO;

// Migración de un solo uso: trae a MongoDB lo que un gimnasio tenía en Medusa
// (productos, planes, stock y pedidos), conservando sus ids, para poder apagar
// Medusa y su base en Neon.
//
//  - La URL y la llave de admin de Medusa llegan en la petición y no se
//    guardan: después de migrar no hacen falta.
//  - No duplica: un producto o pedido que ya está en MongoDB se queda como
//    está. Correrla dos veces no cambia nada la segunda.
//  - Los pedidos ya tenían su copia en MongoDB (la escribía el aviso de
//    Medusa), así que sus recibos siguen saliendo; aquí se agregan los que
//    falten y se les anota qué piezas tomaron del inventario.
//  - El stock de cada variante queda: existencias = las de Medusa;
//    apartadas = lo de pedidos pagados o por pagar que no se entregaron.
@Service
public class MigracionMedusaService {

    private static final Logger log = LoggerFactory.getLogger(MigracionMedusaService.class);
    private static final int POR_PAGINA = 100;
    private static final Set<String> PAGOS_COBRADOS = Set.of("captured", "partially_refunded");

    private static final String CAMPOS_PRODUCTO = String.join(",",
            "id", "title", "description", "status", "thumbnail", "metadata", "type_id", "created_at",
            "categories.handle", "sales_channels.id",
            "variants.id", "variants.title", "variants.manage_inventory", "variants.metadata",
            "variants.created_at", "variants.variant_rank", "variants.prices.amount", "variants.prices.currency_code",
            "variants.inventory_items.inventory.location_levels.location_id",
            "variants.inventory_items.inventory.location_levels.stocked_quantity");

    private static final String CAMPOS_PEDIDO = String.join(",",
            "id", "display_id", "status", "payment_status", "email", "metadata", "sales_channel_id",
            "created_at", "canceled_at", "total", "original_total", "subtotal", "tax_total",
            "items.id", "items.title", "items.variant_title", "items.product_id", "items.variant_id",
            "items.product_type", "items.quantity", "items.unit_price", "items.total", "items.metadata",
            "payment_collections.payments.provider_id", "payment_collections.payments.captured_at",
            "payment_collections.payments.data");

    private final ProductoRepository productos;
    private final PedidoRepository pedidos;
    private final PaymentRepository pagos;
    private final MongoTemplate mongo;
    private final ObjectMapper json;

    public MigracionMedusaService(ProductoRepository productos, PedidoRepository pedidos, PaymentRepository pagos,
                                  MongoTemplate mongo, ObjectMapper json) {
        this.productos = productos;
        this.pedidos = pedidos;
        this.pagos = pagos;
        this.mongo = mongo;
        this.json = json;
    }

    public Map<String, Object> migrar(String gymId, String url, String llaveAdmin) {
        Medusa medusa = new Medusa(url, llaveAdmin);
        String canal = canalDeVenta(gymId);
        List<String> avisos = new ArrayList<>();

        // ─── Productos y planes ───
        Map<String, String> tipos = new HashMap<>();
        medusa.get("/admin/product-types?fields=id,value&limit=100").path("product_types")
                .forEach(t -> tipos.put(t.path("id").asText(), t.path("value").asText()));
        Map<String, Integer> conteo = new LinkedHashMap<>(Map.of("productosNuevos", 0, "planesNuevos", 0,
                "productosYaEstaban", 0, "pedidosNuevos", 0, "pedidosCompletados", 0, "pedidosYaEstaban", 0));
        Map<String, Producto> nuevos = new LinkedHashMap<>();
        Map<String, Producto.Variante> variantes = new HashMap<>();
        for (JsonNode p : medusa.todos("/admin/products", CAMPOS_PRODUCTO, "products")) {
            if (!delGimnasio(p.path("metadata"), p.path("sales_channels"), null, gymId, canal)) continue;
            Producto existente = productos.findById(p.path("id").asText()).orElse(null);
            if (existente != null) {
                conteo.merge("productosYaEstaban", 1, Integer::sum);
                existente.getVariantes().forEach(v -> variantes.put(v.getId(), v));
                continue;
            }
            Producto nuevo = producto(p, gymId, tipos);
            nuevos.put(nuevo.getId(), nuevo);
            nuevo.getVariantes().forEach(v -> variantes.put(v.getId(), v));
            conteo.merge(nuevo.esPlan() ? "planesNuevos" : "productosNuevos", 1, Integer::sum);
        }

        // ─── Pedidos ───
        for (JsonNode o : medusa.todos("/admin/orders", CAMPOS_PEDIDO, "orders")) {
            if (!delGimnasio(o.path("metadata"), null, o.path("sales_channel_id").asText(""), gymId, canal)) continue;
            Pedido actual = pedidos.findByOrderId(o.path("id").asText()).orElse(null);
            if (actual == null) {
                Pedido p = pedido(o, gymId);
                p.setInventario(inventarioDe(p, variantes));
                if (Pedido.ESTADO_PAGADO.equals(p.getEstado()) && p.incluyePlan()) {
                    // No se aplica un plan viejo de golpe: si su pago no quedó
                    // registrado, el dueño lo revisa en Membresías y Pagos.
                    p.setPlanAplicado(true);
                    if (pagos.findByOrderId(p.getOrderId()).isEmpty()) {
                        avisos.add("El pedido #" + p.getFolio() + " incluía un plan que nunca se aplicó: revisa la membresía de su comprador.");
                    }
                }
                pedidos.save(p);
                conteo.merge("pedidosNuevos", 1, Integer::sum);
            } else if (actual.getInventario() == null) {
                actual.setInventario(inventarioDe(actual, variantes));
                pedidos.save(actual);
                conteo.merge("pedidosCompletados", 1, Integer::sum);
            } else {
                conteo.merge("pedidosYaEstaban", 1, Integer::sum);
            }
        }

        // ─── Stock apartado de los productos nuevos ───
        Map<String, Integer> apartadas = new HashMap<>();
        for (Pedido p : pedidos.findByGymIdOrderByCreadoEnDesc(gymId)) {
            if (p.getInventario() == null || !Pedido.Inventario.APARTADO.equals(p.getInventario().getEstado())) continue;
            p.getInventario().getPiezas().forEach(x -> apartadas.merge(x.getVarianteId(), x.getCantidad(), Integer::sum));
        }
        for (Producto p : nuevos.values()) {
            for (Producto.Variante v : p.getVariantes()) {
                if (!v.isControlarInventario()) continue;
                v.setApartadas(apartadas.getOrDefault(v.getId(), 0));
                v.setDisponible(v.getExistencias() - v.getApartadas());
            }
            productos.insert(p);
        }

        // Los folios nuevos siguen después del más alto que ya existe.
        pedidos.findAll().stream().map(Pedido::getFolio).filter(f -> f != null).max(Long::compare)
                .ifPresent(max -> mongo.getCollection("contadores").updateOne(new Document("_id", "folio"),
                        new Document("$max", new Document("valor", max)),
                        new com.mongodb.client.model.UpdateOptions().upsert(true)));

        Map<String, Object> r = new LinkedHashMap<>(conteo);
        r.put("avisos", avisos);
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
                for (JsonNode nivel : v.path("inventory_items").path(0).path("inventory").path("location_levels")) {
                    existencias += nivel.path("stocked_quantity").asInt(0);
                }
                y.setExistencias(existencias);
                y.setDisponible(existencias);
            }
            lista.add(y);
        }
        x.setVariantes(lista);
        return x;
    }

    // ═══════════════════════════ PEDIDOS ═══════════════════════════

    // Lo mismo que hacía la copia del aviso de Medusa (PedidoService.sincronizar).
    private Pedido pedido(JsonNode o, String gymId) {
        JsonNode metadata = o.path("metadata");
        Pedido p = new Pedido();
        p.setOrderId(o.path("id").asText());
        p.setFolio(o.path("display_id").asLong());
        p.setEmail(texto(o.path("email")));
        p.setCanal(metadata.path("canal").asText(Pedido.CANAL_WEB));
        p.setUserId(texto(metadata.path("userId")));
        p.setGymId(gymId);
        boolean reembolsado = o.path("payment_status").asText().contains("refunded");
        p.setTotal(o.path(reembolsado ? "original_total" : "total").asDouble());
        p.setSubtotal(o.path("subtotal").asDouble());
        p.setIva(o.path("tax_total").asDouble());
        p.setCreadoEn(instante(o.path("created_at")));

        JsonNode pago = o.path("payment_collections").path(0).path("payments").path(0);
        p.setProveedorPago(texto(pago.path("provider_id")));
        p.setCliente(texto(metadata.path("cliente")));
        p.setVendedorId(texto(metadata.path("vendedorId")));
        p.setDatosPago(datosPago(pago.path("data"), metadata.path("efectivo")));

        String estado;
        String pagoEstado = o.path("payment_status").asText();
        if ("canceled".equals(o.path("status").asText())) estado = Pedido.ESTADO_CANCELADO;
        else if ("refunded".equals(pagoEstado)) estado = Pedido.ESTADO_REEMBOLSADO;
        else if (PAGOS_COBRADOS.contains(pagoEstado)) estado = Pedido.ESTADO_PAGADO;
        else estado = Pedido.ESTADO_PENDIENTE_PAGO;
        p.setEstado(estado);
        if (Pedido.ESTADO_PAGADO.equals(estado) || Pedido.ESTADO_REEMBOLSADO.equals(estado)) {
            Instant capturado = instante(pago.path("captured_at"));
            p.setPagadoEn(capturado != null ? capturado : p.getCreadoEn());
        }
        if (Pedido.ESTADO_CANCELADO.equals(estado)) {
            Instant cancelado = instante(o.path("canceled_at"));
            p.setCanceladoEn(cancelado != null ? cancelado : p.getCreadoEn());
        }

        List<Pedido.Partida> partidas = new ArrayList<>();
        for (JsonNode item : o.path("items")) {
            Pedido.Partida x = new Pedido.Partida();
            x.setProductoId(texto(item.path("product_id")));
            x.setVarianteId(texto(item.path("variant_id")));
            x.setTitulo(item.path("title").asText());
            x.setVariante(texto(item.path("variant_title")));
            x.setTipo(item.path("product_type").asText(TIPO_PRODUCTO));
            x.setCantidad(item.path("quantity").asInt());
            x.setPrecioUnitario(item.path("unit_price").asDouble());
            x.setTotal(item.path("total").asDouble());
            partidas.add(x);
        }
        p.setPartidas(partidas);
        p.setSincronizadoEn(Instant.now());
        return p;
    }

    // Qué tomó el pedido del inventario: el mostrador entrega en el acto; la
    // tienda y la app dejan lo vendido (o por pagar) apartado. Lo cancelado o
    // reembolsado ya no tiene nada.
    private static Pedido.Inventario inventarioDe(Pedido p, Map<String, Producto.Variante> variantes) {
        List<Pedido.Pieza> piezas = new ArrayList<>();
        for (Pedido.Partida x : p.getPartidas()) {
            Producto.Variante v = x.getVarianteId() == null ? null : variantes.get(x.getVarianteId());
            if (v == null || !v.isControlarInventario() || x.getCantidad() == null || x.getCantidad() <= 0) continue;
            Pedido.Pieza pieza = new Pedido.Pieza();
            pieza.setProductoId(x.getProductoId());
            pieza.setVarianteId(x.getVarianteId());
            pieza.setCantidad(x.getCantidad());
            piezas.add(pieza);
        }
        String estado;
        if (Pedido.ESTADO_CANCELADO.equals(p.getEstado()) || Pedido.ESTADO_REEMBOLSADO.equals(p.getEstado())) {
            estado = Pedido.Inventario.DEVUELTO;
        } else if (Pedido.CANAL_MOSTRADOR.equals(p.getCanal())) {
            estado = Pedido.Inventario.DESCONTADO;
        } else {
            estado = Pedido.Inventario.APARTADO;
        }
        return InventarioService.registro(estado, piezas);
    }

    private Map<String, Object> datosPago(JsonNode data, JsonNode efectivo) {
        Map<String, Object> datos = new LinkedHashMap<>();
        if (data.isObject()) {
            datos.putAll(json.convertValue(data, new TypeReference<Map<String, Object>>() {}));
            datos.remove("token");
            datos.remove("session_id");
        }
        if (efectivo.isObject()) {
            datos.put("recibido", efectivo.path("recibido").asDouble());
            datos.put("cambio", efectivo.path("cambio").asDouble());
        }
        return datos;
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
