package com.gymtrack.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.gymtrack.model.Carrito;
import com.gymtrack.model.Pedido;
import com.gymtrack.model.TiendaGym;
import com.gymtrack.model.User;
import com.gymtrack.repository.CarritoRepository;
import com.gymtrack.util.MetodosPago;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static com.gymtrack.service.MedusaClient.q;
import static com.gymtrack.service.TiendaGymService.TIPO_MEMBRESIA;

// Carrito del miembro. Las partidas, precios y totales viven en Medusa; aquí
// se aplican las reglas de GymTrack antes de tocarlo:
//  - solo se agrega lo que está publicado en la tienda de su gimnasio;
//  - nunca más piezas de las disponibles;
//  - un solo plan que extienda la membresía por compra, de uno en uno;
//  - antes de cobrar se revisa todo otra vez (algo pudo agotarse, ocultarse o
//    cambiar de precio desde que lo agregó) y, si cambió, se avisa en lugar de
//    cobrar un total distinto al que vio.
@Service
public class CarritoService {

    private static final Logger log = LoggerFactory.getLogger(CarritoService.class);
    public static final Set<String> CANALES = Set.of(Pedido.CANAL_WEB, Pedido.CANAL_APP);
    private static final int MAX_POR_PARTIDA = 20;

    private static final String CAMPOS = String.join(",",
            "id", "completed_at", "email", "total", "subtotal", "tax_total",
            "items.id", "items.title", "items.variant_title", "items.thumbnail", "items.variant_id",
            "items.product_id", "items.product_type", "items.quantity", "items.unit_price", "items.total",
            "items.metadata", "items.created_at",
            "shipping_methods.id", "shipping_methods.shipping_option_id");

    private final MedusaClient medusa;
    private final TiendaGymService tiendas;
    private final EscaparateService escaparate;
    private final CarritoRepository carritos;
    private final PedidoService pedidos;

    public CarritoService(MedusaClient medusa, TiendaGymService tiendas, EscaparateService escaparate,
                          CarritoRepository carritos, PedidoService pedidos) {
        this.medusa = medusa;
        this.tiendas = tiendas;
        this.escaparate = escaparate;
        this.carritos = carritos;
        this.pedidos = pedidos;
    }

    // Todo lo que una operación necesita saber del carrito de esa persona.
    private record Contexto(User user, String gymId, String canal, TiendaGym tienda, Carrito registro) {
        String llave() { return tienda.getPublishableKey(); }
    }

    // ═══════════════════════════ OPERACIONES ═══════════════════════════

    public Map<String, Object> ver(User user, String canal) {
        Contexto c = contexto(user, canal);
        JsonNode cart = vigente(c);
        if (cart == null) return vistaVacia(c);
        List<JsonNode> listado = escaparate.listado(c.gymId());
        List<String> avisos = revisar(c, cart, listado);
        if (!avisos.isEmpty()) cart = leer(c);
        return vista(c, cart, avisos, listado);
    }

    public Map<String, Object> asegurar(User user, String canal) {
        Contexto c = contexto(user, canal);
        JsonNode cart = vigente(c);
        if (cart == null) cart = crear(c);
        return vista(c, cart, List.of(), escaparate.listado(c.gymId()));
    }

    public Map<String, Object> agregar(User user, String canal, String varianteId, Integer cantidad) {
        int piezas = cantidad == null ? 1 : cantidad;
        if (varianteId == null || varianteId.isBlank()) {
            throw new TiendaException(HttpStatus.BAD_REQUEST, "Elige qué presentación quieres.");
        }
        if (piezas < 1 || piezas > MAX_POR_PARTIDA) {
            throw new TiendaException(HttpStatus.BAD_REQUEST, "Puedes llevar de 1 a " + MAX_POR_PARTIDA + " piezas.");
        }
        Contexto c = contexto(user, canal);
        List<JsonNode> listado = escaparate.listado(c.gymId());
        JsonNode producto = escaparate.productoDeVariante(listado, varianteId)
                .orElseThrow(() -> new TiendaException(HttpStatus.NOT_FOUND, "Ese producto ya no está a la venta."));
        JsonNode variante = EscaparateService.variante(producto, varianteId);
        String titulo = producto.path("title").asText();
        boolean esPlan = TIPO_MEMBRESIA.equals(escaparate.tipo(producto));

        JsonNode cart = vigente(c);
        if (cart == null) cart = crear(c);
        JsonNode existente = partidaDeVariante(cart, varianteId);

        Map<String, Object> metadata = new HashMap<>();
        if (esPlan) {
            if (existente != null) {
                throw new TiendaException(HttpStatus.CONFLICT, "«" + titulo + "» ya está en tu carrito.");
            }
            piezas = 1;
            Optional<PlanPagado> plan = CatalogoService.leerPlan(producto);
            if (plan.isPresent()) {
                JsonNode otro = planEnCarrito(cart);
                if (otro != null) {
                    throw new TiendaException(HttpStatus.CONFLICT, "Solo puedes llevar un plan por compra. Quita «"
                            + otro.path("title").asText() + "» para elegir otro.");
                }
                // La duración viaja con la partida: PedidoService la usa al activar
                // la membresía aunque el plan se borre o cambie después.
                metadata.put("duracionUnidad", plan.get().unidad());
                metadata.put("duracionCantidad", plan.get().cantidad());
            }
        }

        int total = piezas + (existente == null ? 0 : existente.path("quantity").asInt());
        validarExistencias(variante, total, titulo);
        try {
            if (existente != null) {
                medusa.storePost(c.llave(), "/store/carts/" + cart.path("id").asText() + "/line-items/"
                        + existente.path("id").asText() + q("fields", "id"), Map.of("quantity", total));
            } else {
                Map<String, Object> partida = new HashMap<>();
                partida.put("variant_id", varianteId);
                partida.put("quantity", piezas);
                if (!metadata.isEmpty()) partida.put("metadata", metadata);
                medusa.storePost(c.llave(), "/store/carts/" + cart.path("id").asText() + "/line-items" + q("fields", "id"), partida);
            }
        } catch (TiendaException e) {
            throw traducir(e, titulo);
        }
        tocar(c);
        return vista(c, leer(c), List.of(), escaparate.listado(c.gymId()));
    }

    public Map<String, Object> cambiarCantidad(User user, String canal, String partidaId, Integer cantidad) {
        if (cantidad == null || cantidad <= 0) return quitar(user, canal, partidaId);
        if (cantidad > MAX_POR_PARTIDA) {
            throw new TiendaException(HttpStatus.BAD_REQUEST, "Puedes llevar hasta " + MAX_POR_PARTIDA + " piezas.");
        }
        Contexto c = contexto(user, canal);
        JsonNode cart = exigirVigente(c);
        JsonNode partida = exigirPartida(cart, partidaId);
        String titulo = partida.path("title").asText();
        if (TIPO_MEMBRESIA.equals(partida.path("product_type").asText()) && cantidad > 1) {
            throw new TiendaException(HttpStatus.BAD_REQUEST, "Los planes se compran de uno en uno.");
        }
        List<JsonNode> listado = escaparate.listado(c.gymId());
        JsonNode producto = escaparate.productoDeVariante(listado, partida.path("variant_id").asText()).orElse(null);
        if (producto == null) {
            borrarPartida(c, cart, partidaId);
            throw new TiendaException(HttpStatus.CONFLICT, "Quitamos «" + titulo + "» porque ya no está a la venta.",
                    Map.of("carrito", vista(c, leer(c), List.of(), listado)));
        }
        validarExistencias(EscaparateService.variante(producto, partida.path("variant_id").asText()), cantidad, titulo);
        try {
            medusa.storePost(c.llave(), "/store/carts/" + cart.path("id").asText() + "/line-items/" + partidaId
                    + q("fields", "id"), Map.of("quantity", cantidad));
        } catch (TiendaException e) {
            throw traducir(e, titulo);
        }
        tocar(c);
        return vista(c, leer(c), List.of(), escaparate.listado(c.gymId()));
    }

    public Map<String, Object> quitar(User user, String canal, String partidaId) {
        Contexto c = contexto(user, canal);
        JsonNode cart = exigirVigente(c);
        exigirPartida(cart, partidaId);
        borrarPartida(c, cart, partidaId);
        tocar(c);
        return vista(c, leer(c), List.of(), escaparate.listado(c.gymId()));
    }

    // Deja el carrito vacío: el siguiente producto abre uno nuevo en Medusa.
    public Map<String, Object> vaciar(User user, String canal) {
        Contexto c = contexto(user, canal);
        if (c.registro().getCartId() != null) {
            c.registro().setCartId(null);
            tocar(c);
        }
        return vistaVacia(c);
    }

    // Cobra el carrito con el método elegido y devuelve el pedido ya guardado.
    //
    // datos es lo que armó el módulo JS del método (js/pagos/*-sim.js): llega
    // tal cual al proveedor de Medusa en la sesión de pago. totalVisto es el
    // total que la persona tenía en pantalla al pulsar "Pagar".
    public Pedido checkout(User user, String canal, String metodo, Map<String, Object> datos, Double totalVisto) {
        String proveedor = proveedorDe(metodo);
        Contexto c = contexto(user, canal);
        JsonNode cart = vigente(c);
        if (cart == null || cart.path("items").isEmpty()) {
            throw new TiendaException(HttpStatus.BAD_REQUEST, "Tu carrito está vacío.");
        }

        // 1. Revisión final: lo que cambió se avisa y no se cobra.
        List<JsonNode> listado = escaparate.listado(c.gymId());
        List<String> avisos = revisar(c, cart, listado);
        cart = leer(c);
        if (!avisos.isEmpty()) {
            throw new TiendaException(HttpStatus.CONFLICT, "Tu carrito cambió. Revísalo antes de pagar.",
                    Map.of("avisos", avisos, "carrito", vista(c, cart, avisos, listado)));
        }
        if (cart.path("items").isEmpty()) {
            throw new TiendaException(HttpStatus.BAD_REQUEST, "Tu carrito está vacío.");
        }
        double total = cart.path("total").asDouble();
        if (totalVisto != null && Math.abs(total - totalVisto) > 0.009) {
            throw new TiendaException(HttpStatus.CONFLICT, "El total de tu carrito cambió a " + dinero(total) + ". Revísalo antes de pagar.",
                    Map.of("carrito", vista(c, cart, List.of(), listado)));
        }

        // 2. Comprador y entrega ("Recoger en el gimnasio").
        String cartId = cart.path("id").asText();
        medusa.storePost(c.llave(), "/store/carts/" + cartId + q("fields", "id"), Map.of("email", user.getEmail()));
        boolean conEntrega = false;
        for (JsonNode m : cart.path("shipping_methods")) {
            if (c.tienda().getShippingOptionId().equals(m.path("shipping_option_id").asText())) conEntrega = true;
        }
        if (!conEntrega) {
            medusa.storePost(c.llave(), "/store/carts/" + cartId + "/shipping-methods" + q("fields", "id"),
                    Map.of("option_id", c.tienda().getShippingOptionId()));
        }

        // 3. Sesión de pago con el simulador elegido y cierre del carrito.
        String coleccion = medusa.storePost(c.llave(), "/store/payment-collections" + q("fields", "id"),
                Map.of("cart_id", cartId)).path("payment_collection").path("id").asText();
        medusa.storePost(c.llave(), "/store/payment-collections/" + coleccion + "/payment-sessions" + q("fields", "id"),
                Map.of("provider_id", proveedor, "data", datos == null ? Map.of() : datos));
        JsonNode resultado;
        try {
            resultado = medusa.storePost(c.llave(), "/store/carts/" + cartId + "/complete", Map.of());
        } catch (TiendaException e) {
            if (e.getMessage() != null && e.getMessage().contains("already being completed")) {
                throw new TiendaException(HttpStatus.CONFLICT, "Tu pago ya se está procesando. Espera unos segundos.");
            }
            throw e;
        }
        if (!"order".equals(resultado.path("type").asText())) {
            // El simulador rechazó el pago (tarjeta rechazada, PayPal cancelado...):
            // su mensaje ya viene en español para el comprador.
            String mensaje = resultado.path("error").path("message").asText("No se pudo cobrar. Intenta con otro método.");
            throw new TiendaException(HttpStatus.PAYMENT_REQUIRED, mensaje);
        }

        // 4. El carrito se cerró: el próximo producto abre uno nuevo.
        String orderId = resultado.path("order").path("id").asText();
        c.registro().setCartId(null);
        tocar(c);
        log.info("Pedido {} creado desde el carrito {} ({}, {}).", orderId, cartId, MetodosPago.nombre(proveedor), c.canal());
        return pedidos.sincronizar(orderId);
    }

    // ═══════════════════════════ REVISIÓN ═══════════════════════════

    // Compara el carrito con lo que hoy está a la venta y lo corrige. Devuelve
    // un aviso por cada cosa que cambió, en palabras del comprador.
    private List<String> revisar(Contexto c, JsonNode cart, List<JsonNode> listado) {
        List<String> avisos = new ArrayList<>();
        for (JsonNode partida : cart.path("items")) {
            String partidaId = partida.path("id").asText();
            String titulo = partida.path("title").asText();
            String varianteId = partida.path("variant_id").asText();
            JsonNode producto = escaparate.productoDeVariante(listado, varianteId).orElse(null);
            if (producto == null) {
                borrarPartida(c, cart, partidaId);
                avisos.add("Quitamos «" + titulo + "» porque ya no está a la venta.");
                continue;
            }
            JsonNode variante = EscaparateService.variante(producto, varianteId);
            Integer disponible = EscaparateService.disponible(variante);
            int cantidad = partida.path("quantity").asInt();
            int nueva = cantidad;
            if (disponible != null && cantidad > disponible) {
                if (disponible <= 0) {
                    borrarPartida(c, cart, partidaId);
                    avisos.add("«" + titulo + "» se agotó y lo quitamos de tu carrito.");
                    continue;
                }
                nueva = disponible;
                avisos.add("Solo quedan " + disponible + " de «" + titulo + "»: ajustamos la cantidad.");
            }
            double vigente = EscaparateService.precio(variante);
            double anterior = partida.path("unit_price").asDouble();
            boolean cambioDePrecio = Math.abs(vigente - anterior) > 0.009;
            if (cambioDePrecio) {
                avisos.add("El precio de «" + titulo + "» cambió de " + dinero(anterior) + " a " + dinero(vigente) + ".");
            }
            // Medusa solo recalcula el precio de una partida cuando se actualiza:
            // se reescribe su cantidad (aunque sea la misma) para que tome el vigente.
            if (nueva != cantidad || cambioDePrecio) {
                medusa.storePost(c.llave(), "/store/carts/" + cart.path("id").asText() + "/line-items/" + partidaId
                        + q("fields", "id"), Map.of("quantity", nueva));
            }
        }
        return avisos;
    }

    private void validarExistencias(JsonNode variante, int cantidad, String titulo) {
        Integer disponible = EscaparateService.disponible(variante);
        if (disponible == null || cantidad <= disponible) return;
        if (disponible <= 0) throw new TiendaException(HttpStatus.CONFLICT, "«" + titulo + "» se agotó.");
        throw new TiendaException(HttpStatus.CONFLICT, "Solo quedan " + disponible + " piezas de «" + titulo + "».");
    }

    // Medusa también cuida el inventario; si gana la carrera, su mensaje se traduce.
    private TiendaException traducir(TiendaException e, String titulo) {
        if (e.getStatus() == HttpStatus.BAD_REQUEST && e.getMessage() != null && e.getMessage().contains("inventory")) {
            return new TiendaException(HttpStatus.CONFLICT, "Ya no hay suficientes piezas de «" + titulo + "».");
        }
        return e;
    }

    // ═══════════════════════════ CARRITO EN MEDUSA ═══════════════════════════

    private Contexto contexto(User user, String canal) {
        String elegido = canal == null || canal.isBlank() ? Pedido.CANAL_WEB : canal.trim().toLowerCase();
        if (!CANALES.contains(elegido)) {
            throw new TiendaException(HttpStatus.BAD_REQUEST, "Canal no válido (web o app).");
        }
        // El carrito es del gimnasio actual del miembro: si se cambió de
        // gimnasio, el carrito anterior simplemente deja de aparecer.
        String gymId = user.getGymId();
        TiendaGym tienda = tiendas.asegurar(gymId);
        Carrito registro = carritos.findByUserIdAndGymIdAndCanal(user.getId(), gymId, elegido)
                .orElseGet(() -> new Carrito(user.getId(), gymId, elegido));
        return new Contexto(user, gymId, elegido, tienda, registro);
    }

    // El carrito abierto en Medusa, o null si no hay (nunca agregó nada, ya se
    // pagó o Medusa ya no lo tiene).
    private JsonNode vigente(Contexto c) {
        if (c.registro().getCartId() == null) return null;
        try {
            JsonNode cart = leer(c);
            if (!cart.path("completed_at").isNull() && !cart.path("completed_at").isMissingNode()) {
                c.registro().setCartId(null);
                tocar(c);
                return null;
            }
            return cart;
        } catch (TiendaException e) {
            if (e.getStatus() != HttpStatus.NOT_FOUND) throw e;
            c.registro().setCartId(null);
            tocar(c);
            return null;
        }
    }

    private JsonNode exigirVigente(Contexto c) {
        JsonNode cart = vigente(c);
        if (cart == null) throw new TiendaException(HttpStatus.NOT_FOUND, "Tu carrito está vacío.");
        return cart;
    }

    private JsonNode crear(Contexto c) {
        // metadata viaja al pedido: así Spring sabe de quién es y por dónde compró.
        JsonNode cart = medusa.storePost(c.llave(), "/store/carts" + q("fields", "id"), Map.of(
                "region_id", tiendas.base().regionId(),
                "email", c.user().getEmail(),
                "metadata", Map.of("userId", c.user().getId(), "gymId", c.gymId(), "canal", c.canal()))).path("cart");
        c.registro().setCartId(cart.path("id").asText());
        tocar(c);
        return leer(c);
    }

    private JsonNode leer(Contexto c) {
        return medusa.storeGet(c.llave(), "/store/carts/" + c.registro().getCartId() + q("fields", CAMPOS)).path("cart");
    }

    private void borrarPartida(Contexto c, JsonNode cart, String partidaId) {
        medusa.storeDelete(c.llave(), "/store/carts/" + cart.path("id").asText() + "/line-items/" + partidaId);
    }

    // Guarda el registro. Si dos dispositivos crearon el primer carrito a la vez,
    // el segundo choca con el índice único y se queda con el del primero.
    private void tocar(Contexto c) {
        c.registro().setActualizadoEn(Instant.now());
        try {
            carritos.save(c.registro());
        } catch (DuplicateKeyException carrera) {
            Carrito existente = carritos.findByUserIdAndGymIdAndCanal(c.user().getId(), c.gymId(), c.canal()).orElseThrow();
            c.registro().setId(existente.getId());
            carritos.save(c.registro());
        }
    }

    private static JsonNode partidaDeVariante(JsonNode cart, String varianteId) {
        for (JsonNode p : cart.path("items")) {
            if (varianteId.equals(p.path("variant_id").asText())) return p;
        }
        return null;
    }

    private static JsonNode exigirPartida(JsonNode cart, String partidaId) {
        for (JsonNode p : cart.path("items")) {
            if (partidaId.equals(p.path("id").asText())) return p;
        }
        throw new TiendaException(HttpStatus.NOT_FOUND, "Ese producto ya no está en tu carrito.");
    }

    // Plan que extiende la membresía (la inscripción de pago único no cuenta).
    private static JsonNode planEnCarrito(JsonNode cart) {
        for (JsonNode p : cart.path("items")) {
            if (TIPO_MEMBRESIA.equals(p.path("product_type").asText())
                    && PlanPagado.UNIDADES.contains(p.path("metadata").path("duracionUnidad").asText())) {
                return p;
            }
        }
        return null;
    }

    private static String proveedorDe(String metodo) {
        if (metodo == null) metodo = "";
        return switch (metodo) {
            case "stripe" -> MetodosPago.STRIPE;
            case "paynet" -> MetodosPago.PAYNET;
            case "paypal" -> MetodosPago.PAYPAL;
            default -> throw new TiendaException(HttpStatus.BAD_REQUEST, "Elige cómo quieres pagar.");
        };
    }

    // ═══════════════════════════ VISTA ═══════════════════════════

    // Forma en que la web y la app reciben el carrito.
    private Map<String, Object> vista(Contexto c, JsonNode cart, List<String> avisos, List<JsonNode> listado) {
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("id", cart.path("id").asText());
        v.put("gymId", c.gymId());
        v.put("canal", c.canal());
        List<Map<String, Object>> items = new ArrayList<>();
        int articulos = 0;
        List<JsonNode> partidas = new ArrayList<>();
        cart.path("items").forEach(partidas::add);
        partidas.sort((a, b) -> a.path("created_at").asText("").compareTo(b.path("created_at").asText("")));
        for (JsonNode p : partidas) {
            boolean esPlan = TIPO_MEMBRESIA.equals(p.path("product_type").asText());
            String varianteId = p.path("variant_id").asText();
            Integer maximo = escaparate.productoDeVariante(listado, varianteId)
                    .map(prod -> EscaparateService.disponible(EscaparateService.variante(prod, varianteId)))
                    .orElse(null);
            Map<String, Object> x = new LinkedHashMap<>();
            x.put("id", p.path("id").asText());
            x.put("varianteId", varianteId);
            x.put("productoId", p.path("product_id").asText());
            x.put("titulo", p.path("title").asText());
            x.put("variante", esPlan ? textoDelPlan(p) : p.path("variant_title").asText(null));
            x.put("imagen", p.path("thumbnail").isNull() ? null : p.path("thumbnail").asText(null));
            x.put("esPlan", esPlan);
            x.put("cantidad", p.path("quantity").asInt());
            x.put("maximo", esPlan ? Integer.valueOf(1) : maximo == null ? Integer.valueOf(MAX_POR_PARTIDA) : Integer.valueOf(Math.min(maximo, MAX_POR_PARTIDA)));
            x.put("precioUnitario", p.path("unit_price").asDouble());
            x.put("total", p.path("total").asDouble());
            items.add(x);
            articulos += p.path("quantity").asInt();
        }
        v.put("items", items);
        v.put("articulos", articulos);
        v.put("subtotal", cart.path("subtotal").asDouble());
        v.put("iva", cart.path("tax_total").asDouble());
        v.put("total", cart.path("total").asDouble());
        v.put("avisos", avisos);
        return v;
    }

    private Map<String, Object> vistaVacia(Contexto c) {
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("id", null);
        v.put("gymId", c.gymId());
        v.put("canal", c.canal());
        v.put("items", List.of());
        v.put("articulos", 0);
        v.put("subtotal", 0.0);
        v.put("iva", 0.0);
        v.put("total", 0.0);
        v.put("avisos", List.of());
        return v;
    }

    private static String textoDelPlan(JsonNode partida) {
        JsonNode m = partida.path("metadata");
        String unidad = m.path("duracionUnidad").asText("");
        int cantidad = m.path("duracionCantidad").asInt(0);
        if (!PlanPagado.UNIDADES.contains(unidad) || cantidad < 1) return "Pago único";
        return CatalogoService.duracionTexto(new PlanPagado(null, null, unidad, cantidad));
    }

    private static String dinero(double monto) {
        return String.format(java.util.Locale.forLanguageTag("es-MX"), "$%,.2f", monto);
    }
}
