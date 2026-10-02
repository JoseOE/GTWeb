package com.gymtrack.service;

import com.fasterxml.jackson.databind.JsonNode;
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
            "created_at", "canceled_at", "total", "subtotal", "tax_total",
            "items.id", "items.title", "items.variant_title", "items.product_id", "items.variant_id",
            "items.product_type", "items.quantity", "items.unit_price", "items.total", "items.metadata",
            "payment_collections.payments.provider_id", "payment_collections.payments.captured_at");

    private final MedusaClient medusa;
    private final PedidoRepository pedidoRepository;
    private final GymRepository gymRepository;
    private final UserRepository userRepository;
    private final BillingService billingService;

    public PedidoService(MedusaClient medusa, PedidoRepository pedidoRepository, GymRepository gymRepository,
                         UserRepository userRepository, BillingService billingService) {
        this.medusa = medusa;
        this.pedidoRepository = pedidoRepository;
        this.gymRepository = gymRepository;
        this.userRepository = userRepository;
        this.billingService = billingService;
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
        p.setTotal(o.path("total").asDouble());
        p.setSubtotal(o.path("subtotal").asDouble());
        p.setIva(o.path("tax_total").asDouble());
        p.setCreadoEn(instante(o.path("created_at")));

        JsonNode pago = o.path("payment_collections").path(0).path("payments").path(0);
        p.setProveedorPago(texto(pago.path("provider_id")));

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
