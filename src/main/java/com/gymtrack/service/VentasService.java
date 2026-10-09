package com.gymtrack.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.gymtrack.model.Pedido;
import com.gymtrack.model.User;
import com.gymtrack.repository.PedidoRepository;
import com.gymtrack.repository.UserRepository;
import com.gymtrack.util.MetodosPago;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Date;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static com.gymtrack.service.MedusaClient.q;

// Dashboard de ventas del panel (pestaña Ventas). Todo sale de la colección
// "pedidos": el resumen es una sola agregación de MongoDB (no se traen los
// pedidos a memoria) y los índices por gimnasio y fecha la mantienen rápida.
// Solo las acciones (reembolsar, cancelar) pasan por Medusa.
//
// Una venta es un pedido pagado; cuenta el día en que se pagó, en la hora de
// México. Los reembolsados y cancelados no suman.
@Service
public class VentasService {

    private static final Logger log = LoggerFactory.getLogger(VentasService.class);
    private static final ZoneId ZONA_MX = ZoneId.of("America/Mexico_City");
    private static final int DIAS_POR_DEFECTO = 30;
    private static final int MAX_DIAS = 366;
    private static final Map<String, String> METODOS = Map.of(
            "tarjeta", MetodosPago.STRIPE,
            "stripe", MetodosPago.STRIPE,
            "paynet", MetodosPago.PAYNET,
            "paypal", MetodosPago.PAYPAL,
            "efectivo", MetodosPago.EFECTIVO);
    private static final Set<String> ESTADOS = Set.of(Pedido.ESTADO_PENDIENTE_PAGO, Pedido.ESTADO_PAGADO,
            Pedido.ESTADO_CANCELADO, Pedido.ESTADO_REEMBOLSADO);
    private static final Set<String> CANALES = Set.of(Pedido.CANAL_WEB, Pedido.CANAL_APP, Pedido.CANAL_MOSTRADOR);

    private final PedidoRepository pedidoRepository;
    private final PedidoService pedidos;
    private final UserRepository userRepository;
    private final MedusaClient medusa;
    private final BillingService billing;
    private final MongoTemplate mongo;

    public VentasService(PedidoRepository pedidoRepository, PedidoService pedidos, UserRepository userRepository,
                         MedusaClient medusa, BillingService billing, MongoTemplate mongo) {
        this.pedidoRepository = pedidoRepository;
        this.pedidos = pedidos;
        this.userRepository = userRepository;
        this.medusa = medusa;
        this.billing = billing;
        this.mongo = mongo;
    }

    // ═══════════════════════════ RESUMEN ═══════════════════════════

    // KPIs de hoy y del mes, y las gráficas del periodo (por defecto, los
    // últimos 30 días): ventas por día, por método, por canal y los productos
    // que más venden. Todo en una agregación: $facet arma cada bloque a partir
    // de los pedidos pagados del gimnasio desde el día más antiguo que se pide.
    public Map<String, Object> resumen(String gymId, LocalDate desde, LocalDate hasta) {
        LocalDate hoy = LocalDate.now(ZONA_MX);
        LocalDate fin = hasta == null ? hoy : hasta;
        LocalDate inicio = desde == null ? fin.minusDays(DIAS_POR_DEFECTO - 1L) : desde;
        validarPeriodo(inicio, fin);
        LocalDate inicioMes = hoy.withDayOfMonth(1);
        LocalDate primero = inicio.isBefore(inicioMes) ? inicio : inicioMes;
        Date desdeInstante = Date.from(primero.atStartOfDay(ZONA_MX).toInstant());

        // Una venta cuenta el día en que se pagó (hora de México).
        Document fecha = new Document("$dateToString", new Document("format", "%Y-%m-%d")
                .append("date", new Document("$ifNull", List.of("$pagadoEn", "$creadoEn")))
                .append("timezone", ZONA_MX.getId()));
        Document delPeriodo = rango(inicio, fin);
        List<Document> pipeline = List.of(
                new Document("$match", new Document("gymId", gymId).append("estado", Pedido.ESTADO_PAGADO)
                        .append("$or", List.of(
                                new Document("pagadoEn", new Document("$gte", desdeInstante)),
                                new Document("pagadoEn", null).append("creadoEn", new Document("$gte", desdeInstante))))),
                new Document("$project", new Document("fecha", fecha)
                        .append("total", new Document("$ifNull", List.of("$total", 0)))
                        .append("proveedorPago", 1)
                        .append("canal", new Document("$ifNull", List.of("$canal", Pedido.CANAL_WEB)))
                        .append("partidas", 1)),
                new Document("$facet", new Document()
                        .append("hoy", List.of(rango(hoy, hoy), grupoSuma(null)))
                        .append("mes", List.of(rango(inicioMes, hoy), grupoSuma(null)))
                        .append("periodo", List.of(delPeriodo, grupoSuma(null)))
                        .append("porDia", List.of(delPeriodo, grupoSuma("$fecha")))
                        .append("porMetodo", List.of(delPeriodo, grupoSuma("$proveedorPago")))
                        .append("porCanal", List.of(delPeriodo, grupoSuma("$canal")))
                        .append("topProductos", List.of(delPeriodo,
                                new Document("$unwind", "$partidas"),
                                new Document("$group", new Document("_id", "$partidas.titulo")
                                        .append("tipo", new Document("$first", "$partidas.tipo"))
                                        .append("cantidad", new Document("$sum", new Document("$ifNull", List.of("$partidas.cantidad", 0))))
                                        .append("total", new Document("$sum", new Document("$ifNull", List.of("$partidas.total", 0))))),
                                new Document("$sort", new Document("total", -1).append("_id", 1)),
                                new Document("$limit", 5)))
                        .append("membresiasDelMes", List.of(rango(inicioMes, hoy),
                                new Document("$unwind", "$partidas"),
                                new Document("$match", new Document("partidas.tipo", TiendaGymService.TIPO_MEMBRESIA)),
                                new Document("$group", new Document("_id", null)
                                        .append("cantidad", new Document("$sum", new Document("$ifNull", List.of("$partidas.cantidad", 0)))))))));
        Document r = mongo.getCollection("pedidos").aggregate(pipeline).first();
        if (r == null) r = new Document();

        Map<String, Object> mes = sumaDe(r, "mes");
        Map<String, Object> kpis = new LinkedHashMap<>();
        kpis.put("hoy", sumaDe(r, "hoy"));
        kpis.put("mes", mes);
        int pedidosMes = (int) mes.get("pedidos");
        kpis.put("ticketPromedio", pedidosMes == 0 ? 0.0 : centavos((double) mes.get("total") / pedidosMes));
        kpis.put("paynetPendientes", paynetPendientes(gymId));
        List<Document> membresias = r.getList("membresiasDelMes", Document.class, List.of());
        kpis.put("membresiasDelMes", membresias.isEmpty() ? 0 : numero(membresias.get(0).get("cantidad")).intValue());

        // Un punto por día del periodo, aunque ese día no se haya vendido nada.
        Map<String, Document> porFecha = new HashMap<>();
        r.getList("porDia", Document.class, List.of()).forEach(d -> porFecha.put(d.getString("_id"), d));
        List<Map<String, Object>> porDia = new ArrayList<>();
        for (LocalDate d = inicio; !d.isAfter(fin); d = d.plusDays(1)) {
            Map<String, Object> punto = new LinkedHashMap<>();
            punto.put("fecha", d.toString());
            punto.putAll(sumaDe(porFecha.get(d.toString())));
            porDia.add(punto);
        }

        Map<String, Object> resultado = new LinkedHashMap<>();
        resultado.put("desde", inicio.toString());
        resultado.put("hasta", fin.toString());
        resultado.put("kpis", kpis);
        resultado.put("periodo", sumaDe(r, "periodo"));
        resultado.put("porDia", porDia);
        resultado.put("porMetodo", grupos(r.getList("porMetodo", Document.class, List.of()), MetodosPago::nombre));
        resultado.put("porCanal", grupos(r.getList("porCanal", Document.class, List.of()), c -> c));
        resultado.put("topProductos", r.getList("topProductos", Document.class, List.of()).stream().map(d -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("titulo", d.get("_id"));
            m.put("tipo", d.get("tipo"));
            m.put("cantidad", numero(d.get("cantidad")).intValue());
            m.put("total", centavos(numero(d.get("total")).doubleValue()));
            return m;
        }).toList());
        return resultado;
    }

    // Fichas Paynet que todavía no se pagan (sin importar la fecha).
    private Map<String, Object> paynetPendientes(String gymId) {
        Document r = mongo.getCollection("pedidos").aggregate(List.of(
                new Document("$match", new Document("gymId", gymId)
                        .append("estado", Pedido.ESTADO_PENDIENTE_PAGO)
                        .append("proveedorPago", MetodosPago.PAYNET)),
                grupoSuma(null))).first();
        return sumaDe(r);
    }

    // Etapa $match por días (yyyy-MM-dd, ya en la hora de México).
    private static Document rango(LocalDate desde, LocalDate hasta) {
        return new Document("$match", new Document("fecha",
                new Document("$gte", desde.toString()).append("$lte", hasta.toString())));
    }

    // Etapa $group que cuenta pedidos y suma su total (agrupando por "clave", o todo junto).
    private static Document grupoSuma(String clave) {
        return new Document("$group", new Document("_id", clave)
                .append("pedidos", new Document("$sum", 1))
                .append("total", new Document("$sum", "$total")));
    }

    private static Map<String, Object> sumaDe(Document facet, String nombre) {
        List<Document> lista = facet.getList(nombre, Document.class, List.of());
        return sumaDe(lista.isEmpty() ? null : lista.get(0));
    }

    private static Map<String, Object> sumaDe(Document grupo) {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("pedidos", grupo == null ? 0 : numero(grupo.get("pedidos")).intValue());
        s.put("total", grupo == null ? 0.0 : centavos(numero(grupo.get("total")).doubleValue()));
        return s;
    }

    // Grupos de mayor a menor total, con el nombre que ve el dueño.
    private static List<Map<String, Object>> grupos(List<Document> lista, Function<String, String> nombre) {
        List<Map<String, Object>> salida = new ArrayList<>();
        for (Document d : lista) {
            Map<String, Object> g = new LinkedHashMap<>();
            g.put("nombre", nombre.apply(d.getString("_id")));
            g.putAll(sumaDe(d));
            salida.add(g);
        }
        salida.sort(Comparator.comparingDouble((Map<String, Object> g) -> (Double) g.get("total")).reversed()
                .thenComparing(g -> String.valueOf(g.get("nombre"))));
        return salida;
    }

    private static Number numero(Object n) {
        return n instanceof Number num ? num : 0;
    }

    // ═══════════════════════════ PEDIDOS ═══════════════════════════

    public record Filtros(String metodo, String estado, String canal, LocalDate desde, LocalDate hasta, String buscar) {}

    // Lista paginada de pedidos (todos los estados), del más nuevo al más viejo.
    public Map<String, Object> pedidos(String gymId, Filtros f, Integer pagina, Integer tamano) {
        int tam = tamano == null ? 20 : Math.max(1, Math.min(tamano, 100));
        int pag = pagina == null ? 0 : Math.max(0, pagina);
        if (f.desde() != null && f.hasta() != null && f.desde().isAfter(f.hasta())) {
            throw new TiendaException(HttpStatus.BAD_REQUEST, "La fecha inicial va después de la final.");
        }

        List<Pedido> filtrados = pedidoRepository.findByGymIdOrderByCreadoEnDesc(gymId).stream()
                .filter(filtro(f))
                .toList();
        List<Pedido> pagina0 = filtrados.stream().skip((long) pag * tam).limit(tam).toList();
        Map<String, User> miembros = miembros(pagina0);

        Map<String, Object> r = new LinkedHashMap<>();
        r.put("pedidos", pagina0.stream().map(p -> fila(p, miembros)).toList());
        r.put("total", filtrados.size());
        r.put("pagina", pag);
        r.put("paginas", (int) Math.ceil(filtrados.size() / (double) tam));
        // Lo cobrado de verdad en lo filtrado: solo los pagados.
        r.put("cobrado", centavos(total(filtrados.stream().filter(p -> Pedido.ESTADO_PAGADO.equals(p.getEstado())).toList())));
        return r;
    }

    private Predicate<Pedido> filtro(Filtros f) {
        Predicate<Pedido> pred = p -> true;
        if (vacio(f.metodo()) != null) {
            String proveedor = METODOS.get(f.metodo().trim().toLowerCase());
            if (proveedor == null) throw new TiendaException(HttpStatus.BAD_REQUEST, "Método no válido (tarjeta, paynet, paypal o efectivo).");
            pred = pred.and(p -> proveedor.equals(p.getProveedorPago()));
        }
        if (vacio(f.estado()) != null) {
            String estado = f.estado().trim().toLowerCase();
            if (!ESTADOS.contains(estado)) throw new TiendaException(HttpStatus.BAD_REQUEST, "Estado no válido.");
            pred = pred.and(p -> estado.equals(p.getEstado()));
        }
        if (vacio(f.canal()) != null) {
            String canal = f.canal().trim().toLowerCase();
            if (!CANALES.contains(canal)) throw new TiendaException(HttpStatus.BAD_REQUEST, "Canal no válido (web, app o mostrador).");
            pred = pred.and(p -> canal.equals(p.getCanal()));
        }
        if (f.desde() != null) pred = pred.and(p -> !fechaDeCreacion(p).isBefore(f.desde()));
        if (f.hasta() != null) pred = pred.and(p -> !fechaDeCreacion(p).isAfter(f.hasta()));
        String buscar = vacio(f.buscar());
        if (buscar != null) {
            String texto = EscaparateService.normalizar(buscar.replace("#", "").trim());
            pred = pred.and(p -> (p.getFolio() != null && String.valueOf(p.getFolio()).equals(texto))
                    || (p.getCliente() != null && EscaparateService.normalizar(p.getCliente()).contains(texto))
                    || (p.getEmail() != null && p.getEmail().toLowerCase().contains(texto)));
        }
        return pred;
    }

    private Map<String, Object> fila(Pedido p, Map<String, User> miembros) {
        Map<String, Object> f = new LinkedHashMap<>();
        f.put("orderId", p.getOrderId());
        f.put("folio", p.getFolio());
        f.put("creadoEn", p.getCreadoEn());
        f.put("pagadoEn", p.getPagadoEn());
        f.put("cliente", nombreCliente(p, miembros));
        f.put("canal", p.getCanal());
        f.put("metodo", MetodosPago.nombre(p.getProveedorPago()));
        f.put("detallePago", PedidoService.detallePago(p));
        f.put("estado", p.getEstado());
        f.put("total", p.getTotal());
        f.put("articulos", p.getPartidas().stream().mapToInt(x -> x.getCantidad() == null ? 0 : x.getCantidad()).sum());
        return f;
    }

    // ═══════════════════════════ DETALLE Y ACCIONES ═══════════════════════════

    public Map<String, Object> detalle(String gymId, String orderId) {
        return detalle(exigirPedido(gymId, orderId));
    }

    private Map<String, Object> detalle(Pedido p) {
        Map<String, Object> d = new LinkedHashMap<>(pedidos.vista(p));
        Map<String, User> miembros = miembros(List.of(p));
        d.put("clienteNombre", nombreCliente(p, miembros));
        d.put("email", p.getUserId() == null ? null : p.getEmail());
        d.put("vendedor", p.getVendedorId() == null ? null
                : userRepository.findById(p.getVendedorId()).map(User::getNombre).orElse(null));
        d.put("planAplicado", p.isPlanAplicado());
        boolean pagado = Pedido.ESTADO_PAGADO.equals(p.getEstado());
        boolean pendiente = Pedido.ESTADO_PENDIENTE_PAGO.equals(p.getEstado());
        boolean paynet = MetodosPago.PAYNET.equals(p.getProveedorPago());
        boolean vencida = paynet && SimuladorPaynetService.vencida(p);
        Map<String, Object> acciones = new LinkedHashMap<>();
        // Recibo de un pedido pagado (o reembolsado, como comprobante); la ficha si es Paynet pendiente.
        acciones.put("recibo", pagado || Pedido.ESTADO_REEMBOLSADO.equals(p.getEstado()));
        acciones.put("ficha", pendiente && paynet);
        // El correo solo va a miembros: una venta al público no tiene a quién escribirle.
        acciones.put("reenviar", p.getUserId() != null && (pagado || (pendiente && paynet)));
        acciones.put("simularPaynet", pendiente && paynet && !vencida);
        acciones.put("reembolsar", pagado);
        acciones.put("cancelar", pendiente);
        d.put("acciones", acciones);
        d.put("fichaVencida", vencida);
        return d;
    }

    // Reembolso simulado de un pedido pagado: Medusa devuelve todo lo cobrado
    // (los simuladores no mueven dinero real) y el pedido queda "Reembolsado".
    // Lo vendido no regresa al inventario: el producto ya se entregó. Si el
    // pedido había extendido una membresía, se revierte (ver BillingService).
    public Map<String, Object> reembolsar(String gymId, String orderId) {
        Pedido p = exigirPedido(gymId, orderId);
        if (!Pedido.ESTADO_PAGADO.equals(p.getEstado())) {
            throw new TiendaException(HttpStatus.CONFLICT, "Solo se reembolsan pedidos pagados.");
        }
        JsonNode orden = medusa.adminGet("/admin/orders/" + orderId + q("fields",
                "id,payment_collections.payments.id,payment_collections.payments.amount,"
                        + "payment_collections.payments.captured_at,payment_collections.payments.refunds.amount")).path("order");
        for (JsonNode pago : orden.path("payment_collections").path(0).path("payments")) {
            if (pago.path("captured_at").isNull() || pago.path("captured_at").isMissingNode()) continue;
            double reembolsado = 0;
            for (JsonNode r : pago.path("refunds")) reembolsado += r.path("amount").asDouble();
            double pendiente = centavos(pago.path("amount").asDouble() - reembolsado);
            if (pendiente > 0) {
                medusa.adminPost("/admin/payments/" + pago.path("id").asText() + "/refund" + q("fields", "id"),
                        Map.of("amount", pendiente, "note", "Reembolso simulado desde el panel de GymTrack"));
            }
        }
        Pedido actualizado = pedidos.sincronizar(orderId);
        log.info("Pedido #{} reembolsado desde el panel.", p.getFolio());

        Map<String, Object> r = detalle(actualizado);
        if (p.isPlanAplicado() && p.getUserId() != null) {
            BillingService.Reversion reversion = billing.revertirPagoDePedido(orderId);
            if (reversion != null) {
                r.put("membresia", Map.of(
                        "miembro", reversion.miembro() == null ? "" : reversion.miembro(),
                        "ajustada", reversion.ajustada(),
                        "vence", reversion.vence() == null ? "" : reversion.vence().toString()));
            }
        }
        return r;
    }

    // Cancela un pedido que todavía no se paga (una ficha Paynet): Medusa
    // libera lo apartado. Uno pagado se reembolsa en lugar de cancelarse.
    public Map<String, Object> cancelar(String gymId, String orderId) {
        Pedido p = exigirPedido(gymId, orderId);
        if (!Pedido.ESTADO_PENDIENTE_PAGO.equals(p.getEstado())) {
            throw new TiendaException(HttpStatus.CONFLICT, Pedido.ESTADO_PAGADO.equals(p.getEstado())
                    ? "Ese pedido ya se pagó: reembólsalo en lugar de cancelarlo."
                    : "Ese pedido ya no está pendiente.");
        }
        medusa.adminPost("/admin/orders/" + orderId + "/cancel" + q("fields", "id"), Map.of());
        log.info("Pedido #{} cancelado desde el panel.", p.getFolio());
        return detalle(pedidos.sincronizar(orderId));
    }

    // ═══════════════════════════ AYUDANTES ═══════════════════════════

    private Pedido exigirPedido(String gymId, String orderId) {
        return pedidoRepository.findByOrderId(orderId)
                .filter(p -> gymId.equals(p.getGymId()))
                .orElseThrow(() -> new TiendaException(HttpStatus.NOT_FOUND, "Pedido no encontrado."));
    }

    private Map<String, User> miembros(List<Pedido> lista) {
        List<String> ids = lista.stream().map(Pedido::getUserId).filter(id -> id != null).distinct().toList();
        Map<String, User> m = new HashMap<>();
        userRepository.findAllById(ids).forEach(u -> m.put(u.getId(), u));
        return m;
    }

    // Quién compró: el nombre del miembro, el que se capturó en el mostrador
    // ("Público en general") o, a falta de ambos, el correo.
    private static String nombreCliente(Pedido p, Map<String, User> miembros) {
        User u = p.getUserId() == null ? null : miembros.get(p.getUserId());
        if (u != null && u.getNombre() != null && !u.getNombre().isBlank()) return u.getNombre();
        if (p.getCliente() != null) return p.getCliente();
        return p.getEmail();
    }

    private LocalDate fechaDeVenta(Pedido p) {
        Instant cuando = p.getPagadoEn() != null ? p.getPagadoEn() : p.getCreadoEn();
        return LocalDate.ofInstant(cuando == null ? Instant.now() : cuando, ZONA_MX);
    }

    private static LocalDate fechaDeCreacion(Pedido p) {
        return LocalDate.ofInstant(p.getCreadoEn() == null ? Instant.now() : p.getCreadoEn(), ZONA_MX);
    }

    private static boolean entre(LocalDate d, LocalDate desde, LocalDate hasta) {
        return !d.isBefore(desde) && !d.isAfter(hasta);
    }

    private static void validarPeriodo(LocalDate desde, LocalDate hasta) {
        if (desde.isAfter(hasta)) {
            throw new TiendaException(HttpStatus.BAD_REQUEST, "La fecha inicial va después de la final.");
        }
        if (ChronoUnit.DAYS.between(desde, hasta) >= MAX_DIAS) {
            throw new TiendaException(HttpStatus.BAD_REQUEST, "El periodo puede ser de hasta un año.");
        }
    }

    private static double total(List<Pedido> lista) {
        return lista.stream().mapToDouble(p -> p.getTotal() == null ? 0 : p.getTotal()).sum();
    }

    private static double centavos(double n) {
        return Math.round(n * 100) / 100.0;
    }

    private static String vacio(String s) {
        return s == null || s.isBlank() ? null : s;
    }
}
