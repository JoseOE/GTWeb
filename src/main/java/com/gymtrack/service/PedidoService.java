package com.gymtrack.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gymtrack.model.Gym;
import com.gymtrack.model.Pedido;
import com.gymtrack.model.User;
import com.gymtrack.repository.GymRepository;
import com.gymtrack.repository.PedidoRepository;
import com.gymtrack.repository.UserRepository;
import com.gymtrack.util.MetodosPago;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import static com.gymtrack.service.MedusaClient.q;

// Mantiene la copia local de cada pedido (colección "pedidos") igual a Medusa
// y aplica sus consecuencias: un pedido pagado que incluye un plan extiende la
// membresía del comprador.
//
// sincronizar() se puede llamar las veces que sea con el mismo pedido —desde el
// aviso de Medusa, desde el checkout o al consultar el pedido— y el resultado
// es el mismo: la membresía se extiende una sola vez (ver BillingService).
@Service
public class PedidoService {

    private static final Logger log = LoggerFactory.getLogger(PedidoService.class);
    private static final ZoneId ZONA_MX = ZoneId.of("America/Mexico_City");
    private static final Set<String> PAGOS_COBRADOS = Set.of("captured", "partially_refunded");
    private static final Map<String, String> ESTADOS = Map.of(
            Pedido.ESTADO_PENDIENTE_PAGO, "Pendiente de pago",
            Pedido.ESTADO_PAGADO, "Pagado",
            Pedido.ESTADO_CANCELADO, "Cancelado",
            Pedido.ESTADO_REEMBOLSADO, "Reembolsado");

    private static final String CAMPOS = String.join(",",
            "id", "display_id", "status", "payment_status", "email", "metadata", "sales_channel_id",
            "created_at", "canceled_at", "total", "original_total", "subtotal", "tax_total",
            "items.id", "items.title", "items.variant_title", "items.product_id", "items.variant_id",
            "items.product_type", "items.quantity", "items.unit_price", "items.total", "items.metadata",
            "payment_collections.payments.provider_id", "payment_collections.payments.captured_at",
            "payment_collections.payments.data");

    private final MedusaClient medusa;
    private final PedidoRepository pedidoRepository;
    private final GymRepository gymRepository;
    private final UserRepository userRepository;
    private final BillingService billingService;
    private final ObjectMapper json;
    private final AvisosPedidoService avisos;

    public PedidoService(MedusaClient medusa, PedidoRepository pedidoRepository, GymRepository gymRepository,
                         UserRepository userRepository, BillingService billingService, ObjectMapper json,
                         AvisosPedidoService avisos) {
        this.medusa = medusa;
        this.pedidoRepository = pedidoRepository;
        this.gymRepository = gymRepository;
        this.userRepository = userRepository;
        this.billingService = billingService;
        this.json = json;
        this.avisos = avisos;
    }

    public Pedido sincronizar(String orderId) {
        JsonNode o = medusa.adminGet("/admin/orders/" + orderId + q("fields", CAMPOS)).path("order");
        if (o.isMissingNode()) {
            throw new TiendaException(HttpStatus.NOT_FOUND, "Pedido no encontrado.");
        }

        Pedido p = pedidoRepository.findByOrderId(orderId).orElseGet(Pedido::new);
        copiar(o, p);
        p = guardar(p);

        if (Pedido.ESTADO_PAGADO.equals(p.getEstado()) && !p.isPlanAplicado() && p.incluyePlan()) {
            aplicarPlan(p, o);
        }
        // Correos y push del pedido (en segundo plano; cada uno sale una sola vez).
        avisos.alSincronizar(p, vista(p));
        return p;
    }

    private void copiar(JsonNode o, Pedido p) {
        JsonNode metadata = o.path("metadata");
        p.setOrderId(o.path("id").asText());
        p.setFolio(o.path("display_id").asLong());
        p.setEmail(texto(o.path("email")));
        p.setCanal(metadata.path("canal").asText(Pedido.CANAL_WEB));
        p.setUserId(texto(metadata.path("userId")));
        // El gimnasio se toma del canal de venta, no del metadata que mandó el cliente.
        p.setGymId(gymRepository.findByTiendaSalesChannelId(o.path("sales_channel_id").asText())
                .map(Gym::getId)
                .orElse(texto(metadata.path("gymId"))));
        // Al reembolsar, Medusa agrega una línea de crédito y su "total" baja a 0.
        // El pedido conserva lo que se vendió, que es lo que muestran el recibo y Ventas.
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

        String estado = estado(o);
        p.setEstado(estado);
        if (Pedido.ESTADO_PAGADO.equals(estado) && p.getPagadoEn() == null) {
            Instant capturado = instante(pago.path("captured_at"));
            p.setPagadoEn(capturado != null ? capturado : Instant.now());
        }
        if (Pedido.ESTADO_CANCELADO.equals(estado) && p.getCanceladoEn() == null) {
            Instant cancelado = instante(o.path("canceled_at"));
            p.setCanceladoEn(cancelado != null ? cancelado : Instant.now());
        }

        List<Pedido.Partida> partidas = new ArrayList<>();
        for (JsonNode item : o.path("items")) {
            Pedido.Partida x = new Pedido.Partida();
            x.setProductoId(texto(item.path("product_id")));
            x.setVarianteId(texto(item.path("variant_id")));
            x.setTitulo(item.path("title").asText());
            x.setVariante(texto(item.path("variant_title")));
            x.setTipo(item.path("product_type").asText(TiendaGymService.TIPO_PRODUCTO));
            x.setCantidad(item.path("quantity").asInt());
            x.setPrecioUnitario(item.path("unit_price").asDouble());
            x.setTotal(item.path("total").asDouble());
            partidas.add(x);
        }
        p.setPartidas(partidas);
        p.setSincronizadoEn(Instant.now());
    }

    private static String estado(JsonNode o) {
        if ("canceled".equals(o.path("status").asText())) return Pedido.ESTADO_CANCELADO;
        String pago = o.path("payment_status").asText();
        if ("refunded".equals(pago)) return Pedido.ESTADO_REEMBOLSADO;
        if (PAGOS_COBRADOS.contains(pago)) return Pedido.ESTADO_PAGADO;
        // authorized (Paynet esperando el pago en tienda), not_paid, awaiting...
        return Pedido.ESTADO_PENDIENTE_PAGO;
    }

    // Dos avisos del mismo pedido pueden llegar a la vez: el segundo insert choca
    // con el índice único de orderId y se reintenta como actualización.
    private Pedido guardar(Pedido p) {
        try {
            return pedidoRepository.save(p);
        } catch (DuplicateKeyException carrera) {
            Pedido existente = pedidoRepository.findByOrderId(p.getOrderId()).orElseThrow();
            p.setId(existente.getId());
            p.setPlanAplicado(existente.isPlanAplicado());
            return pedidoRepository.save(p);
        }
    }

    private void aplicarPlan(Pedido p, JsonNode orden) {
        Optional<Pedido.Partida> partidaPlan = Optional.empty();
        Optional<PlanPagado> plan = Optional.empty();
        for (int i = 0; i < p.getPartidas().size() && plan.isEmpty(); i++) {
            Pedido.Partida partida = p.getPartidas().get(i);
            if (!partida.esPlan()) continue;
            plan = duracionDe(orden.path("items").path(i), partida);
            if (plan.isPresent()) partidaPlan = Optional.of(partida);
        }

        User member = p.getUserId() == null ? null : userRepository.findById(p.getUserId()).orElse(null);
        if (plan.isEmpty()) {
            // Solo había conceptos de pago único (inscripción): nada que extender.
            marcarAplicado(p);
            return;
        }
        if (member == null || !"member".equals(member.getRole()) || !Objects.equals(p.getGymId(), member.getGymId())) {
            // Venta de mostrador a "Público en general", o alguien que salió del
            // gimnasio entre la compra y el pago: no hay membresía que extender.
            log.warn("El pedido #{} incluye el plan {} pero no tiene un miembro de ese gimnasio; no se extendió ninguna membresía.",
                    p.getFolio(), plan.get().nombre());
            marcarAplicado(p);
            return;
        }

        LocalDate fecha = LocalDate.ofInstant(p.getPagadoEn() != null ? p.getPagadoEn() : Instant.now(), ZONA_MX);
        billingService.registrarPago(member, p.getGymId(), partidaPlan.get().getTotal(),
                MetodosPago.nombre(p.getProveedorPago()), fecha,
                "Compra en la tienda · pedido #" + p.getFolio(), plan.get(), p.getOrderId());
        marcarAplicado(p);
        log.info("Pedido #{}: plan {} aplicado a {}.", p.getFolio(), plan.get().nombre(), member.getEmail());
    }

    // La duración viaja en el metadata de la partida (la pone el carrito al
    // agregar el plan) para no depender de que el plan siga existiendo. Si no
    // viene, se lee del producto en Medusa.
    private Optional<PlanPagado> duracionDe(JsonNode item, Pedido.Partida partida) {
        JsonNode m = item.path("metadata");
        String unidad = m.path("duracionUnidad").asText("");
        int cantidad = m.path("duracionCantidad").asInt(0);
        if (PlanPagado.UNIDADES.contains(unidad) && cantidad > 0) {
            return Optional.of(new PlanPagado(partida.getProductoId(), partida.getTitulo(), unidad, cantidad));
        }
        if (partida.getProductoId() == null) return Optional.empty();
        try {
            JsonNode producto = medusa.adminGet("/admin/products/" + partida.getProductoId()
                    + q("fields", "id,title,metadata")).path("product");
            return CatalogoService.leerPlan(producto);
        } catch (TiendaException e) {
            if (e.getStatus() == HttpStatus.NOT_FOUND) return Optional.empty();
            throw e;
        }
    }

    // Forma en que la web y la app reciben un pedido (confirmación y "Mis pedidos").
    public Map<String, Object> vista(Pedido p) {
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("orderId", p.getOrderId());
        v.put("folio", p.getFolio());
        v.put("estado", p.getEstado());
        v.put("estadoTexto", ESTADOS.getOrDefault(p.getEstado(), p.getEstado()));
        v.put("metodo", MetodosPago.nombre(p.getProveedorPago()));
        v.put("detallePago", detallePago(p));
        v.put("cliente", p.getCliente());
        // Para la pantalla de la ficha (web y app): referencia y fecha límite.
        Object referencia = p.getDatosPago() == null ? null : p.getDatosPago().get("referencia");
        v.put("paynet", referencia == null ? null : Map.of(
                "referencia", referenciaLegible(referencia.toString()),
                "vence", String.valueOf(p.getDatosPago().getOrDefault("vence", ""))));
        v.put("proveedorPago", p.getProveedorPago());
        v.put("canal", p.getCanal());
        v.put("total", p.getTotal());
        v.put("subtotal", p.getSubtotal());
        v.put("iva", p.getIva());
        v.put("creadoEn", p.getCreadoEn());
        v.put("pagadoEn", p.getPagadoEn());
        v.put("canceladoEn", p.getCanceladoEn());
        List<Map<String, Object>> partidas = new ArrayList<>();
        for (Pedido.Partida x : p.getPartidas()) {
            Map<String, Object> partida = new LinkedHashMap<>();
            partida.put("titulo", x.getTitulo());
            // "Plan" es el nombre interno de la única variante de un plan: no dice nada al comprador.
            partida.put("variante", "Plan".equals(x.getVariante()) ? null : x.getVariante());
            partida.put("tipo", x.getTipo());
            partida.put("cantidad", x.getCantidad());
            partida.put("precioUnitario", x.getPrecioUnitario());
            partida.put("total", x.getTotal());
            partidas.add(partida);
        }
        v.put("partidas", partidas);
        Gym gym = p.getGymId() == null ? null : gymRepository.findById(p.getGymId()).orElse(null);
        v.put("gym", gym == null ? null : Map.of(
                "id", gym.getId(),
                "nombre", gym.getNombre() == null ? "" : gym.getNombre(),
                "direccion", gym.getDireccion() == null ? "" : gym.getDireccion()));
        return v;
    }

    // Datos del pago que se conservan para el ticket y el recibo. El token de la
    // tarjeta no sirve para nada después de cobrar, así que no se copia.
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

    // "Visa •••• 4242", "Efectivo · recibido $500.00, cambio $42.00"... Los
    // demás simuladores agregan aquí su forma (referencia Paynet, cuenta PayPal).
    public static String detallePago(Pedido p) {
        Map<String, Object> d = p.getDatosPago() == null ? Map.of() : p.getDatosPago();
        if (d.get("marca") != null && d.get("ultimos4") != null) {
            return MARCAS.getOrDefault(String.valueOf(d.get("marca")), String.valueOf(d.get("marca")))
                    + " •••• " + d.get("ultimos4");
        }
        if (d.get("recibido") != null) {
            return "Efectivo · recibido " + dinero(d.get("recibido")) + ", cambio " + dinero(d.get("cambio"));
        }
        if (d.get("referencia") != null) {
            return "Paynet · referencia " + referenciaLegible(String.valueOf(d.get("referencia")));
        }
        if (MetodosPago.PAYPAL.equals(p.getProveedorPago()) && d.get("cuenta") != null) {
            return "PayPal · " + d.get("cuenta");
        }
        return MetodosPago.nombre(p.getProveedorPago());
    }

    // "930123456789012345" → "9301 2345 6789 0123 45": así se dicta en la tienda.
    public static String referenciaLegible(String referencia) {
        return referencia.replaceAll("(.{4})(?!$)", "$1 ");
    }

    private static final Map<String, String> MARCAS = Map.of(
            "visa", "Visa", "mastercard", "Mastercard", "amex", "American Express");

    private static String dinero(Object monto) {
        double n = monto instanceof Number num ? num.doubleValue() : 0;
        return String.format(java.util.Locale.forLanguageTag("es-MX"), "$%,.2f", n);
    }

    private void marcarAplicado(Pedido p) {
        p.setPlanAplicado(true);
        pedidoRepository.save(p);
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
}
