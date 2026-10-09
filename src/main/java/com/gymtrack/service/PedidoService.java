package com.gymtrack.service;

import com.gymtrack.model.Gym;
import com.gymtrack.model.Pedido;
import com.gymtrack.model.User;
import com.gymtrack.repository.GymRepository;
import com.gymtrack.repository.PedidoRepository;
import com.gymtrack.repository.ProductoRepository;
import com.gymtrack.repository.UserRepository;
import com.gymtrack.util.Ids;
import com.gymtrack.util.MetodosPago;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
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

// Los pedidos de la tienda (colección "pedidos"): se crean al cobrar y cambian
// de estado con actualizaciones condicionadas (pagar una ficha, cancelar,
// reembolsar). Sus consecuencias: un pedido pagado que incluye un plan extiende
// la membresía del comprador una sola vez (ver BillingService), y uno que se
// cancela o se reembolsa regresa lo que tomó del inventario.
@Service
public class PedidoService {

    private static final Logger log = LoggerFactory.getLogger(PedidoService.class);
    private static final ZoneId ZONA_MX = ZoneId.of("America/Mexico_City");
    private static final Map<String, String> ESTADOS = Map.of(
            Pedido.ESTADO_PENDIENTE_PAGO, "Pendiente de pago",
            Pedido.ESTADO_PAGADO, "Pagado",
            Pedido.ESTADO_CANCELADO, "Cancelado",
            Pedido.ESTADO_REEMBOLSADO, "Reembolsado");

    private final PedidoRepository pedidoRepository;
    private final GymRepository gymRepository;
    private final UserRepository userRepository;
    private final BillingService billingService;
    private final AvisosPedidoService avisos;
    private final InventarioService inventario;
    private final SimuladorPagoService simuladores;
    private final FolioService folios;
    private final ProductoRepository productoRepository;
    private final MongoTemplate mongo;

    public PedidoService(PedidoRepository pedidoRepository, GymRepository gymRepository,
                         UserRepository userRepository, BillingService billingService,
                         AvisosPedidoService avisos, InventarioService inventario, SimuladorPagoService simuladores,
                         FolioService folios, ProductoRepository productoRepository, MongoTemplate mongo) {
        this.pedidoRepository = pedidoRepository;
        this.gymRepository = gymRepository;
        this.userRepository = userRepository;
        this.billingService = billingService;
        this.avisos = avisos;
        this.inventario = inventario;
        this.simuladores = simuladores;
        this.folios = folios;
        this.productoRepository = productoRepository;
        this.mongo = mongo;
    }

    // ═══════════════════════════ COBRAR ═══════════════════════════

    // Una partida de lo que se va a cobrar, ya revisada contra el catálogo.
    public record Linea(String productoId, String varianteId, String titulo, String variante, String tipo,
                        int cantidad, double precioUnitario, boolean controlarInventario,
                        String duracionUnidad, Integer duracionCantidad) {}

    // Todo lo que hace falta para cobrar una venta. entregaInmediata = el
    // mostrador, que entrega en el acto; la tienda y la app dejan lo vendido
    // apartado. extrasPago se agrega a datosPago (lo recibido y el cambio).
    public record Venta(String gymId, String canal, String userId, String email, String cliente, String vendedorId,
                        List<Linea> lineas, String proveedor, Map<String, Object> datos,
                        boolean entregaInmediata, Map<String, Object> extrasPago) {}

    // Cobra una venta y guarda su pedido:
    //  1. toma las piezas del inventario (todo o nada, atómico);
    //  2. cobra con el simulador del método; si lo rechaza, las piezas regresan
    //     y no queda ningún pedido (402 con su mensaje);
    //  3. guarda el pedido con sus partidas congeladas, folio y totales;
    //  4. si quedó pagado, extiende la membresía y manda los avisos; si es una
    //     ficha Paynet, manda la ficha.
    public Pedido cobrar(Venta v) {
        double total = centavos(v.lineas().stream().mapToDouble(l -> l.precioUnitario() * l.cantidad()).sum());
        List<InventarioService.Solicitud> solicitudes = v.lineas().stream()
                .filter(Linea::controlarInventario)
                .map(l -> new InventarioService.Solicitud(l.productoId(), l.varianteId(), l.cantidad(), l.titulo()))
                .toList();
        List<Pedido.Pieza> piezas = v.entregaInmediata() ? inventario.descontar(solicitudes) : inventario.apartar(solicitudes);
        Pedido.Inventario registro = InventarioService.registro(
                v.entregaInmediata() ? Pedido.Inventario.DESCONTADO : Pedido.Inventario.APARTADO, piezas);

        SimuladorPagoService.Resultado pago;
        try {
            pago = simuladores.autorizar(v.proveedor(), v.datos(), total);
        } catch (RuntimeException e) {
            inventario.devolver(registro);
            throw e;
        }

        Instant ahora = Instant.now();
        Pedido p = new Pedido();
        p.setOrderId(Ids.nuevo("order"));
        p.setFolio(folios.siguiente());
        p.setGymId(v.gymId());
        p.setUserId(v.userId());
        p.setEmail(v.email());
        p.setCanal(v.canal());
        p.setCliente(v.cliente());
        p.setVendedorId(v.vendedorId());
        p.setProveedorPago(v.proveedor());
        Map<String, Object> datosPago = new LinkedHashMap<>(pago.datos());
        if (v.extrasPago() != null) datosPago.putAll(v.extrasPago());
        p.setDatosPago(datosPago);
        // Precios con IVA incluido: el IVA se desglosa del total.
        double iva = centavos(total - total / 1.16);
        p.setTotal(total);
        p.setIva(iva);
        p.setSubtotal(centavos(total - iva));
        List<Pedido.Partida> partidas = new ArrayList<>();
        for (Linea l : v.lineas()) {
            Pedido.Partida x = new Pedido.Partida();
            x.setProductoId(l.productoId());
            x.setVarianteId(l.varianteId());
            x.setTitulo(l.titulo());
            x.setVariante(l.variante());
            x.setTipo(l.tipo());
            x.setCantidad(l.cantidad());
            x.setPrecioUnitario(l.precioUnitario());
            x.setTotal(centavos(l.precioUnitario() * l.cantidad()));
            x.setDuracionUnidad(l.duracionUnidad());
            x.setDuracionCantidad(l.duracionCantidad());
            partidas.add(x);
        }
        p.setPartidas(partidas);
        p.setCreadoEn(ahora);
        p.setEstado(pago.capturado() ? Pedido.ESTADO_PAGADO : Pedido.ESTADO_PENDIENTE_PAGO);
        if (pago.capturado()) p.setPagadoEn(ahora);
        p.setInventario(registro);
        try {
            p = pedidoRepository.insert(p);
        } catch (RuntimeException e) {
            inventario.devolver(registro);
            throw e;
        }
        log.info("Pedido #{} creado ({}, {}, {}).", p.getFolio(), MetodosPago.nombre(p.getProveedorPago()), p.getCanal(), p.getEstado());

        if (Pedido.ESTADO_PAGADO.equals(p.getEstado())) confirmarPago(p);
        else avisos.alActualizar(p, vista(p));
        return p;
    }

    // Un pedido acaba de quedar pagado (en el acto, o una ficha Paynet que se
    // pagó en tienda): extiende la membresía si lleva un plan y manda el
    // recibo. Es idempotente: el plan se aplica una sola vez (Payment.orderId
    // es único) y cada correo se aparta antes de salir.
    public void confirmarPago(Pedido p) {
        if (Pedido.ESTADO_PAGADO.equals(p.getEstado()) && !p.isPlanAplicado() && p.incluyePlan()) {
            aplicarPlan(p);
        }
        avisos.alActualizar(p, vista(p));
    }

    private void aplicarPlan(Pedido p) {
        Pedido.Partida partidaPlan = null;
        PlanPagado plan = null;
        for (Pedido.Partida x : p.getPartidas()) {
            if (!x.esPlan()) continue;
            Optional<PlanPagado> duracion = duracionDe(x);
            if (duracion.isPresent()) {
                partidaPlan = x;
                plan = duracion.get();
                break;
            }
        }
        User member = p.getUserId() == null ? null : userRepository.findById(p.getUserId()).orElse(null);
        if (plan == null) {
            // Solo había conceptos de pago único (inscripción): nada que extender.
            marcarAplicado(p);
            return;
        }
        if (member == null || !"member".equals(member.getRole()) || !Objects.equals(p.getGymId(), member.getGymId())) {
            // Venta de mostrador a "Público en general", o alguien que salió del
            // gimnasio entre la compra y el pago: no hay membresía que extender.
            log.warn("El pedido #{} incluye el plan {} pero no tiene un miembro de ese gimnasio; no se extendió ninguna membresía.",
                    p.getFolio(), plan.nombre());
            marcarAplicado(p);
            return;
        }
        LocalDate fecha = LocalDate.ofInstant(p.getPagadoEn() != null ? p.getPagadoEn() : Instant.now(), ZONA_MX);
        billingService.registrarPago(member, p.getGymId(), partidaPlan.getTotal(),
                MetodosPago.nombre(p.getProveedorPago()), fecha,
                "Compra en la tienda · pedido #" + p.getFolio(), plan, p.getOrderId());
        marcarAplicado(p);
        log.info("Pedido #{}: plan {} aplicado a {}.", p.getFolio(), plan.nombre(), member.getEmail());
    }

    // La duración viaja congelada en la partida. Si no viene (pedidos traídos
    // de Medusa), se lee del plan, si todavía existe.
    private Optional<PlanPagado> duracionDe(Pedido.Partida x) {
        if (PlanPagado.esUnidad(x.getDuracionUnidad()) && x.getDuracionCantidad() != null && x.getDuracionCantidad() > 0) {
            return Optional.of(new PlanPagado(x.getProductoId(), x.getTitulo(), x.getDuracionUnidad(), x.getDuracionCantidad()));
        }
        if (x.getProductoId() == null) return Optional.empty();
        return productoRepository.findById(x.getProductoId()).flatMap(CatalogoService::plan);
    }

    // ═══════════════════════════ CAMBIOS DE ESTADO ═══════════════════════════
    // Cada cambio es una actualización condicionada al estado anterior: si dos
    // llegan a la vez (dos clics en "Simular pago", el vencimiento y una
    // cancelación), solo uno gana y el otro recibe 409.

    // La ficha Paynet se pagó en la tienda: el pedido queda pagado (las piezas
    // siguen apartadas hasta entregarse), se activa el plan y sale el recibo.
    public Pedido pagarFicha(Pedido p) {
        Instant ahora = Instant.now();
        Pedido pagado = cambiarEstado(p, Pedido.ESTADO_PENDIENTE_PAGO, new Update()
                .set("estado", Pedido.ESTADO_PAGADO)
                .set("pagadoEn", ahora)
                .set("datosPago.estado", "capturado")
                .set("datosPago.capturadoEn", ahora.toString()));
        if (pagado == null) {
            throw new TiendaException(HttpStatus.CONFLICT, "La ficha ya no está pendiente.");
        }
        log.info("Ficha Paynet del pedido #{} pagada en tienda.", pagado.getFolio());
        confirmarPago(pagado);
        return pagado;
    }

    // Cancela un pedido que todavía no se paga (una ficha Paynet) y regresa lo
    // que tenía apartado.
    public Pedido cancelar(Pedido p) {
        Instant ahora = Instant.now();
        Pedido cancelado = cambiarEstado(p, Pedido.ESTADO_PENDIENTE_PAGO, new Update()
                .set("estado", Pedido.ESTADO_CANCELADO)
                .set("canceladoEn", ahora)
                .set("datosPago.estado", "cancelado")
                .set("datosPago.canceladoEn", ahora.toString()));
        if (cancelado == null) {
            throw new TiendaException(HttpStatus.CONFLICT, "Ese pedido ya no está pendiente.");
        }
        inventario.devolverDePedido(cancelado);
        log.info("Pedido #{} cancelado; se regresó lo apartado.", cancelado.getFolio());
        return cancelado;
    }

    // Reembolso simulado de un pedido pagado: devuelve todo lo cobrado (no se
    // mueve dinero real). El pedido queda "Reembolsado" con su total y su
    // recibo, y lo que vendió regresa al inventario: lo apartado vuelve a estar
    // disponible y lo entregado en el mostrador vuelve a existencias.
    public Pedido reembolsar(Pedido p) {
        Map<String, Object> reembolso = new LinkedHashMap<>();
        reembolso.put("id", SimuladorPagoService.nuevoId("re_sim"));
        reembolso.put("monto", p.getTotal());
        reembolso.put("fecha", Instant.now().toString());
        Pedido reembolsado = cambiarEstado(p, Pedido.ESTADO_PAGADO, new Update()
                .set("estado", Pedido.ESTADO_REEMBOLSADO)
                .set("datosPago.estado", "reembolsado")
                .push("datosPago.reembolsos", reembolso));
        if (reembolsado == null) {
            throw new TiendaException(HttpStatus.CONFLICT, "Solo se reembolsan pedidos pagados.");
        }
        inventario.devolverDePedido(reembolsado);
        log.info("Pedido #{} reembolsado; se regresó lo que vendió al inventario.", reembolsado.getFolio());
        return reembolsado;
    }

    // Aplica el cambio solo si el pedido sigue en el estado esperado. null = ya no estaba.
    private Pedido cambiarEstado(Pedido p, String estadoEsperado, Update cambio) {
        return mongo.findAndModify(
                Query.query(Criteria.where("_id").is(p.getId()).and("estado").is(estadoEsperado)),
                cambio, FindAndModifyOptions.options().returnNew(true), Pedido.class);
    }

    static double centavos(double n) {
        return Math.round(n * 100) / 100.0;
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


}
