package com.gymtrack.service;

import com.gymtrack.model.Carrito;
import com.gymtrack.model.Pedido;
import com.gymtrack.model.Producto;
import com.gymtrack.model.User;
import com.gymtrack.repository.CarritoRepository;
import com.gymtrack.repository.ProductoRepository;
import com.gymtrack.util.Ids;
import com.gymtrack.util.MetodosPago;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

// Carrito del miembro, completo en MongoDB (colección carritos). Aquí se
// aplican las reglas de GymTrack:
//  - solo se agrega lo que está publicado en la tienda de su gimnasio;
//  - nunca más piezas de las disponibles;
//  - un solo plan que extienda la membresía por compra, de uno en uno;
//  - antes de mostrarlo y antes de cobrar se revisa contra el catálogo (algo
//    pudo agotarse, ocultarse, borrarse o cambiar de precio) y, si cambió, se
//    corrige y se avisa en lugar de cobrar un total distinto al que vio.
@Service
public class CarritoService {

    // Canales del carrito del miembro. El mostrador no guarda carrito: su ticket
    // vive en el navegador del panel y llega completo al cobrar.
    public static final Set<String> CANALES = Set.of(Pedido.CANAL_WEB, Pedido.CANAL_APP);
    private static final int MAX_POR_PARTIDA = 20;

    private final CarritoRepository carritos;
    private final ProductoRepository productos;
    private final PedidoService pedidos;
    private final SimuladorStripeService stripe;
    private final SimuladorPaypalService paypal;
    private final MongoTemplate mongo;

    public CarritoService(CarritoRepository carritos, ProductoRepository productos, PedidoService pedidos,
                          SimuladorStripeService stripe, SimuladorPaypalService paypal, MongoTemplate mongo) {
        this.carritos = carritos;
        this.productos = productos;
        this.pedidos = pedidos;
        this.stripe = stripe;
        this.paypal = paypal;
        this.mongo = mongo;
    }

    // Lo publicado en la tienda de un gimnasio, por id de variante: una sola consulta.
    private record Elegible(Producto producto, Producto.Variante variante) {}

    private Map<String, Elegible> catalogo(String gymId) {
        Map<String, Elegible> m = new HashMap<>();
        for (Producto p : productos.findByGymIdAndActivoTrue(gymId)) {
            for (Producto.Variante v : p.getVariantes()) m.put(v.getId(), new Elegible(p, v));
        }
        return m;
    }

    // ═══════════════════════════ OPERACIONES ═══════════════════════════

    public Map<String, Object> ver(User user, String gymId, String canal) {
        Carrito c = carrito(user, gymId, canal);
        if (c.getCartId() == null) return vistaVacia(gymId, canal(canal));
        Map<String, Elegible> cat = catalogo(gymId);
        List<String> avisos = revisar(c, cat);
        // Mientras se cobra solo se muestra: la corrección se guarda después.
        if (!avisos.isEmpty() && !cobrando(c)) guardar(c);
        return vista(c, avisos, cat);
    }

    public Map<String, Object> asegurar(User user, String gymId, String canal) {
        Carrito c = carrito(user, gymId, canal);
        if (c.getCartId() == null) {
            c.setCartId(Ids.nuevo("cart"));
            guardar(c);
        }
        return vista(c, List.of(), catalogo(gymId));
    }

    public Map<String, Object> agregar(User user, String gymId, String canal, String varianteId, Integer cantidad) {
        int piezas = cantidad == null ? 1 : cantidad;
        if (varianteId == null || varianteId.isBlank()) {
            throw new TiendaException(HttpStatus.BAD_REQUEST, "Elige qué presentación quieres.");
        }
        if (piezas < 1 || piezas > MAX_POR_PARTIDA) {
            throw new TiendaException(HttpStatus.BAD_REQUEST, "Puedes llevar de 1 a " + MAX_POR_PARTIDA + " piezas.");
        }
        Map<String, Elegible> cat = catalogo(gymId);
        Elegible e = cat.get(varianteId);
        if (e == null) throw new TiendaException(HttpStatus.NOT_FOUND, "Ese producto ya no está a la venta.");
        String titulo = e.producto().getNombre();

        Carrito c = carrito(user, gymId, canal);
        exigirLibre(c);
        Carrito.Partida existente = partidaDeVariante(c, varianteId);
        Optional<PlanPagado> plan = e.producto().esPlan() ? CatalogoService.plan(e.producto()) : Optional.empty();
        if (e.producto().esPlan()) {
            if (existente != null) {
                throw new TiendaException(HttpStatus.CONFLICT, "«" + titulo + "» ya está en tu carrito.");
            }
            piezas = 1;
            if (plan.isPresent()) {
                Carrito.Partida otro = planEnCarrito(c);
                if (otro != null) {
                    throw new TiendaException(HttpStatus.CONFLICT, "Solo puedes llevar un plan por compra. Quita «"
                            + otro.getTitulo() + "» para elegir otro.");
                }
            }
        }

        int total = piezas + (existente == null ? 0 : existente.getCantidad());
        validarExistencias(e.variante(), total, titulo);
        if (existente != null) {
            existente.setCantidad(total);
        } else {
            Carrito.Partida x = new Carrito.Partida();
            x.setId(Ids.nuevo("cali"));
            x.setProductoId(e.producto().getId());
            x.setVarianteId(varianteId);
            x.setTitulo(titulo);
            x.setVariante(e.variante().etiqueta());
            x.setImagen(e.producto().getImagen());
            x.setEsPlan(e.producto().esPlan());
            // La duración viaja con la partida: el pedido la usa al activar la
            // membresía aunque el plan se borre o cambie después.
            plan.ifPresent(pl -> {
                x.setDuracionUnidad(pl.unidad());
                x.setDuracionCantidad(pl.cantidad());
            });
            x.setCantidad(piezas);
            x.setPrecioUnitario(e.variante().getPrecio());
            x.setAgregadoEn(Instant.now());
            c.getItems().add(x);
        }
        if (c.getCartId() == null) c.setCartId(Ids.nuevo("cart"));
        guardar(c);
        return vista(c, List.of(), cat);
    }

    public Map<String, Object> cambiarCantidad(User user, String gymId, String canal, String partidaId, Integer cantidad) {
        if (cantidad == null || cantidad <= 0) return quitar(user, gymId, canal, partidaId);
        if (cantidad > MAX_POR_PARTIDA) {
            throw new TiendaException(HttpStatus.BAD_REQUEST, "Puedes llevar hasta " + MAX_POR_PARTIDA + " piezas.");
        }
        Carrito c = exigirVigente(user, gymId, canal);
        exigirLibre(c);
        Carrito.Partida partida = exigirPartida(c, partidaId);
        String titulo = partida.getTitulo();
        if (partida.isEsPlan() && cantidad > 1) {
            throw new TiendaException(HttpStatus.BAD_REQUEST, "Los planes se compran de uno en uno.");
        }
        Map<String, Elegible> cat = catalogo(gymId);
        Elegible e = cat.get(partida.getVarianteId());
        if (e == null) {
            c.getItems().remove(partida);
            guardar(c);
            throw new TiendaException(HttpStatus.CONFLICT, "Quitamos «" + titulo + "» porque ya no está a la venta.",
                    Map.of("carrito", vista(c, List.of(), cat)));
        }
        validarExistencias(e.variante(), cantidad, titulo);
        partida.setCantidad(cantidad);
        guardar(c);
        return vista(c, List.of(), cat);
    }

    public Map<String, Object> quitar(User user, String gymId, String canal, String partidaId) {
        Carrito c = exigirVigente(user, gymId, canal);
        exigirLibre(c);
        c.getItems().remove(exigirPartida(c, partidaId));
        guardar(c);
        return vista(c, List.of(), catalogo(gymId));
    }

    // Deja el carrito vacío: el siguiente producto abre uno nuevo.
    public Map<String, Object> vaciar(User user, String gymId, String canal) {
        Carrito c = carrito(user, gymId, canal);
        exigirLibre(c);
        if (c.getCartId() != null || !c.getItems().isEmpty()) {
            c.setCartId(null);
            c.getItems().clear();
            guardar(c);
        }
        return vistaVacia(gymId, c.getCanal());
    }

    // Cobra el carrito con el método elegido y devuelve el pedido ya guardado.
    //
    // datos es lo que armó el módulo JS del método (js/pagos/*-sim.js).
    // totalVisto es el total que la persona tenía en pantalla al pulsar "Pagar".
    public Pedido checkout(User user, String gymId, String canal, String metodo, Map<String, Object> datos, Double totalVisto) {
        String proveedor = proveedorDe(metodo);
        Carrito c = carrito(user, gymId, canal);
        exigirLibre(c);
        Map<String, Elegible> cat = catalogo(gymId);
        revisarParaCobrar(c, cat, totalVisto);
        double total = total(c);
        Instant cobro = tomarCobro(c);
        Pedido pedido;
        try {
            // PayPal llega como la orden que el comprador aprobó en paypal-sim.html:
            // se cambia por sus datos guardados, igual que el token de la tarjeta.
            if (MetodosPago.PAYPAL.equals(proveedor)) {
                datos = paypal.consumir(user.getId(), gymId, c.getCanal(), total, datos);
            }
            // La tarjeta llega como token: se cambia por los datos guardados del token
            // para que el resultado del cobro no se pueda inventar desde el navegador.
            if (MetodosPago.STRIPE.equals(proveedor)) datos = stripe.consumir(user.getId(), datos);

            pedido = pedidos.cobrar(new PedidoService.Venta(gymId, c.getCanal(), user.getId(), user.getEmail(),
                    null, null, lineas(c, cat), proveedor, datos, false, null));
        } catch (RuntimeException e) {
            // No se cobró: el carrito queda como estaba para intentar con otro método.
            soltarCobro(c, cobro, new Update());
            throw e;
        }
        // El carrito se pagó: el próximo producto abre uno nuevo.
        soltarCobro(c, cobro, new Update().set("cartId", null).set("items", List.of()));
        return pedido;
    }

    // Una partida del ticket del mostrador, tal como la manda el panel.
    public record PartidaTicket(String varianteId, Integer cantidad) {}

    public record VentaMostrador(Pedido pedido, Double cambio) {}

    // Venta en el mostrador del panel: la cobra el dueño, en efectivo o con la
    // tarjeta (el simulador de Stripe hace de terminal), a un miembro o al
    // público en general, y se entrega en el acto (descuenta existencias).
    public VentaMostrador cobrarMostrador(User dueno, String gymId, User cliente, List<PartidaTicket> partidas,
                                          String metodo, Double recibido, Map<String, Object> datos, Double totalVisto) {
        if (!"efectivo".equals(metodo) && !"tarjeta".equals(metodo)) {
            throw new TiendaException(HttpStatus.BAD_REQUEST, "Elige cobrar en efectivo o con tarjeta.");
        }
        List<PedidoService.Linea> lineas = revisarTicket(catalogo(gymId), partidas, totalVisto);
        double total = PedidoService.centavos(lineas.stream().mapToDouble(l -> l.precioUnitario() * l.cantidad()).sum());
        if ("efectivo".equals(metodo) && (recibido == null || recibido + 0.009 < total)) {
            throw new TiendaException(HttpStatus.BAD_REQUEST, "El efectivo recibido no alcanza para " + dinero(total) + ".");
        }
        String proveedor;
        Double cambio = null;
        Map<String, Object> extras = null;
        if ("efectivo".equals(metodo)) {
            proveedor = MetodosPago.EFECTIVO;
            cambio = PedidoService.centavos(recibido - total);
            extras = Map.of("recibido", recibido, "cambio", cambio);
            datos = Map.of();
        } else {
            proveedor = MetodosPago.STRIPE;
            datos = stripe.consumir(dueno.getId(), datos);
        }
        // En una venta al público el correo del pedido es el del dueño y no se
        // manda ningún aviso.
        Pedido pedido = pedidos.cobrar(new PedidoService.Venta(gymId, Pedido.CANAL_MOSTRADOR,
                cliente == null ? null : cliente.getId(),
                cliente == null ? dueno.getEmail() : cliente.getEmail(),
                cliente == null ? "Público en general" : cliente.getNombre(),
                dueno.getId(), lineas, proveedor, datos, true, extras));
        return new VentaMostrador(pedido, cambio);
    }

    // Revisa el ticket del mostrador contra lo que hoy está a la venta: solo lo
    // publicado, nunca más piezas de las disponibles y un solo plan que
    // extienda la membresía. Si algo cambió, se avisa y no se cobra.
    private List<PedidoService.Linea> revisarTicket(Map<String, Elegible> cat, List<PartidaTicket> partidas, Double totalVisto) {
        if (partidas == null || partidas.isEmpty()) {
            throw new TiendaException(HttpStatus.BAD_REQUEST, "El ticket está vacío.");
        }
        // Una sola partida por presentación, en el orden en que se agregaron.
        Map<String, Integer> cantidades = new LinkedHashMap<>();
        for (PartidaTicket p : partidas) {
            if (p == null || p.varianteId() == null || p.varianteId().isBlank()) {
                throw new TiendaException(HttpStatus.BAD_REQUEST, "Hay una partida sin presentación en el ticket.");
            }
            cantidades.merge(p.varianteId(), p.cantidad() == null ? 1 : p.cantidad(), Integer::sum);
        }
        List<PedidoService.Linea> lineas = new ArrayList<>();
        List<String> avisos = new ArrayList<>();
        String plan = null;
        for (Map.Entry<String, Integer> en : cantidades.entrySet()) {
            Elegible e = cat.get(en.getKey());
            int cantidad = en.getValue();
            if (e == null) {
                avisos.add("Un producto del ticket ya no está a la venta.");
                continue;
            }
            String titulo = e.producto().getNombre();
            if (cantidad < 1 || cantidad > MAX_POR_PARTIDA) {
                throw new TiendaException(HttpStatus.BAD_REQUEST, "De «" + titulo + "» se venden de 1 a " + MAX_POR_PARTIDA + " piezas.");
            }
            Optional<PlanPagado> duracion = Optional.empty();
            if (e.producto().esPlan()) {
                if (cantidad > 1) throw new TiendaException(HttpStatus.BAD_REQUEST, "Los planes se venden de uno en uno.");
                duracion = CatalogoService.plan(e.producto());
                if (duracion.isPresent()) {
                    if (plan != null) {
                        throw new TiendaException(HttpStatus.BAD_REQUEST, "Solo puede ir un plan por venta: «" + plan + "» y «" + titulo + "».");
                    }
                    plan = titulo;
                }
            }
            Integer disponible = disponible(e.variante());
            if (disponible != null && cantidad > disponible) {
                avisos.add(disponible <= 0 ? "«" + titulo + "» se agotó." : "Solo quedan " + disponible + " de «" + titulo + "».");
                continue;
            }
            lineas.add(linea(e, cantidad, duracion.orElse(null)));
        }
        if (!avisos.isEmpty()) {
            throw new TiendaException(HttpStatus.CONFLICT, "El ticket cambió. Revísalo antes de cobrar.", Map.of("avisos", avisos));
        }
        double total = PedidoService.centavos(lineas.stream().mapToDouble(l -> l.precioUnitario() * l.cantidad()).sum());
        if (totalVisto == null || Math.abs(total - totalVisto) > 0.009) {
            throw new TiendaException(HttpStatus.CONFLICT, "El total cambió a " + dinero(total) + ". Revisa el ticket antes de cobrar.");
        }
        return lineas;
    }

    // Revisión final antes de cobrar: lo que cambió se corrige, se avisa y no se cobra.
    private void revisarParaCobrar(Carrito c, Map<String, Elegible> cat, Double totalVisto) {
        if (c.getCartId() == null || c.getItems().isEmpty()) {
            throw new TiendaException(HttpStatus.BAD_REQUEST, "El carrito está vacío.");
        }
        List<String> avisos = revisar(c, cat);
        if (!avisos.isEmpty()) {
            guardar(c);
            throw new TiendaException(HttpStatus.CONFLICT, "El carrito cambió. Revísalo antes de cobrar.",
                    Map.of("avisos", avisos, "carrito", vista(c, avisos, cat)));
        }
        double total = total(c);
        if (totalVisto != null && Math.abs(total - totalVisto) > 0.009) {
            throw new TiendaException(HttpStatus.CONFLICT, "El total cambió a " + dinero(total) + ". Revísalo antes de cobrar.",
                    Map.of("carrito", vista(c, List.of(), cat)));
        }
    }

    private List<PedidoService.Linea> lineas(Carrito c, Map<String, Elegible> cat) {
        List<PedidoService.Linea> lineas = new ArrayList<>();
        for (Carrito.Partida x : enOrden(c)) {
            Elegible e = cat.get(x.getVarianteId());
            PlanPagado plan = x.getDuracionUnidad() == null ? null
                    : new PlanPagado(x.getProductoId(), x.getTitulo(), x.getDuracionUnidad(), x.getDuracionCantidad());
            lineas.add(linea(e, x.getCantidad(), plan));
        }
        return lineas;
    }

    private static PedidoService.Linea linea(Elegible e, int cantidad, PlanPagado plan) {
        Producto p = e.producto();
        Producto.Variante v = e.variante();
        return new PedidoService.Linea(p.getId(), v.getId(), p.getNombre(),
                p.esPlan() ? CatalogoService.VARIANTE_PLAN : v.etiqueta(),
                p.esPlan() ? Producto.TIPO_MEMBRESIA : Producto.TIPO_PRODUCTO,
                cantidad, v.getPrecio(), v.isControlarInventario(),
                plan == null ? null : plan.unidad(), plan == null ? null : plan.cantidad());
    }

    // ═══════════════════════════ REVISIÓN ═══════════════════════════

    // Compara el carrito con lo que hoy está a la venta y lo corrige. Devuelve
    // un aviso por cada cosa que cambió, en palabras del comprador.
    private List<String> revisar(Carrito c, Map<String, Elegible> cat) {
        List<String> avisos = new ArrayList<>();
        for (Carrito.Partida partida : new ArrayList<>(enOrden(c))) {
            String titulo = partida.getTitulo();
            Elegible e = cat.get(partida.getVarianteId());
            if (e == null) {
                c.getItems().remove(partida);
                avisos.add("Quitamos «" + titulo + "» porque ya no está a la venta.");
                continue;
            }
            Integer disponible = disponible(e.variante());
            int cantidad = partida.getCantidad();
            if (disponible != null && cantidad > disponible) {
                if (disponible <= 0) {
                    c.getItems().remove(partida);
                    avisos.add("«" + titulo + "» se agotó y lo quitamos de tu carrito.");
                    continue;
                }
                partida.setCantidad(disponible);
                avisos.add("Solo quedan " + disponible + " de «" + titulo + "»: ajustamos la cantidad.");
            }
            double vigente = e.variante().getPrecio();
            double anterior = partida.getPrecioUnitario();
            if (Math.abs(vigente - anterior) > 0.009) {
                avisos.add("El precio de «" + titulo + "» cambió de " + dinero(anterior) + " a " + dinero(vigente) + ".");
                partida.setPrecioUnitario(vigente);
            }
        }
        return avisos;
    }

    private static void validarExistencias(Producto.Variante variante, int cantidad, String titulo) {
        Integer disponible = disponible(variante);
        if (disponible == null || cantidad <= disponible) return;
        if (disponible <= 0) throw new TiendaException(HttpStatus.CONFLICT, "«" + titulo + "» se agotó.");
        throw new TiendaException(HttpStatus.CONFLICT, "Solo quedan " + disponible + " piezas de «" + titulo + "».");
    }

    // null = no controla inventario (nunca se agota).
    private static Integer disponible(Producto.Variante v) {
        return v.isControlarInventario() ? Math.max(0, v.getDisponible()) : null;
    }

    // ═══════════════════════════ CARRITO EN MONGODB ═══════════════════════════

    // CarritoController pasa el gimnasio actual del miembro: si se cambió de
    // gimnasio, el carrito anterior simplemente deja de aparecer.
    private Carrito carrito(User user, String gymId, String canal) {
        String elegido = canal(canal);
        Carrito c = carritos.findByUserIdAndGymIdAndCanal(user.getId(), gymId, elegido)
                .orElseGet(() -> new Carrito(user.getId(), gymId, elegido));
        if (c.getId() != null && c.getVersion() == null) {
            // Carrito de cuando la tienda vivía en Medusa: no tenía versión ni
            // partidas. Se le pone versión 0 para que se actualice en lugar de
            // intentar insertarlo otra vez.
            mongo.updateFirst(Query.query(Criteria.where("_id").is(c.getId()).and("version").exists(false)),
                    new Update().set("version", 0L), Carrito.class);
            c.setVersion(0L);
        }
        return c;
    }

    private Carrito exigirVigente(User user, String gymId, String canal) {
        Carrito c = carrito(user, gymId, canal);
        if (c.getCartId() == null) throw new TiendaException(HttpStatus.NOT_FOUND, "Tu carrito está vacío.");
        return c;
    }

    // ═══════════════════════════ CANDADO DE COBRO ═══════════════════════════
    // Un doble clic en "Pagar" o el mismo carrito pagado desde la computadora y
    // el celular a la vez: solo el primero cobra. Mientras cobra, el carrito no
    // se cambia. Si el servidor se cae a medio cobro, el candado vence solo.

    private static final Duration COBRO_VENCE = Duration.ofMinutes(2);

    private static boolean cobrando(Carrito c) {
        return c.getCobrandoDesde() != null && c.getCobrandoDesde().isAfter(Instant.now().minus(COBRO_VENCE));
    }

    private static void exigirLibre(Carrito c) {
        if (cobrando(c)) {
            throw new TiendaException(HttpStatus.CONFLICT, "Tu pago ya se está procesando. Espera unos segundos.");
        }
    }

    // Toma el candado solo si el carrito sigue tal como se revisó (misma
    // versión) y nadie más lo está cobrando. Devuelve la marca del candado.
    private Instant tomarCobro(Carrito c) {
        // Al milisegundo, como lo guarda MongoDB: así se reconoce al soltarlo.
        Instant ahora = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        Carrito tomado = mongo.findAndModify(
                Query.query(Criteria.where("_id").is(c.getId()).and("version").is(c.getVersion())
                        .orOperator(Criteria.where("cobrandoDesde").is(null),
                                Criteria.where("cobrandoDesde").lt(ahora.minus(COBRO_VENCE)))),
                new Update().set("cobrandoDesde", ahora).inc("version", 1),
                FindAndModifyOptions.options().returnNew(true), Carrito.class);
        if (tomado == null) {
            Carrito actual = carritos.findById(c.getId()).orElse(c);
            exigirLibre(actual);
            throw new TiendaException(HttpStatus.CONFLICT, "Tu carrito cambió en otra sesión. Revísalo antes de cobrar.");
        }
        c.setVersion(tomado.getVersion());
        c.setCobrandoDesde(ahora);
        return ahora;
    }

    // Suelta el candado (solo el propio) con el cambio indicado.
    private void soltarCobro(Carrito c, Instant cobro, Update cambio) {
        mongo.updateFirst(Query.query(Criteria.where("_id").is(c.getId()).and("cobrandoDesde").is(cobro)),
                cambio.unset("cobrandoDesde").set("actualizadoEn", Instant.now()).inc("version", 1), Carrito.class);
    }

    private void guardar(Carrito c) {
        c.setActualizadoEn(Instant.now());
        Carrito guardado = carritos.save(c);
        c.setId(guardado.getId());
        c.setVersion(guardado.getVersion());
    }

    private static String canal(String canal) {
        String elegido = canal == null || canal.isBlank() ? Pedido.CANAL_WEB : canal.trim().toLowerCase();
        if (!CANALES.contains(elegido)) {
            throw new TiendaException(HttpStatus.BAD_REQUEST, "Canal no válido (web o app).");
        }
        return elegido;
    }

    private static List<Carrito.Partida> enOrden(Carrito c) {
        return c.getItems().stream()
                .sorted(Comparator.comparing(x -> x.getAgregadoEn() == null ? Instant.EPOCH : x.getAgregadoEn()))
                .toList();
    }

    private static Carrito.Partida partidaDeVariante(Carrito c, String varianteId) {
        return c.getItems().stream().filter(p -> varianteId.equals(p.getVarianteId())).findFirst().orElse(null);
    }

    private static Carrito.Partida exigirPartida(Carrito c, String partidaId) {
        return c.getItems().stream().filter(p -> p.getId().equals(partidaId)).findFirst()
                .orElseThrow(() -> new TiendaException(HttpStatus.NOT_FOUND, "Ese producto ya no está en tu carrito."));
    }

    // Plan que extiende la membresía (la inscripción de pago único no cuenta).
    private static Carrito.Partida planEnCarrito(Carrito c) {
        return c.getItems().stream()
                .filter(p -> p.isEsPlan() && PlanPagado.UNIDADES.contains(p.getDuracionUnidad()))
                .findFirst().orElse(null);
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

    private static double total(Carrito c) {
        return PedidoService.centavos(c.getItems().stream().mapToDouble(x -> x.getPrecioUnitario() * x.getCantidad()).sum());
    }

    // ═══════════════════════════ VISTA ═══════════════════════════

    // Forma en que la web y la app reciben el carrito.
    private Map<String, Object> vista(Carrito c, List<String> avisos, Map<String, Elegible> cat) {
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("id", c.getCartId());
        v.put("gymId", c.getGymId());
        v.put("canal", c.getCanal());
        List<Map<String, Object>> items = new ArrayList<>();
        int articulos = 0;
        for (Carrito.Partida p : enOrden(c)) {
            Elegible e = cat.get(p.getVarianteId());
            Integer maximo = e == null ? null : disponible(e.variante());
            Map<String, Object> x = new LinkedHashMap<>();
            x.put("id", p.getId());
            x.put("varianteId", p.getVarianteId());
            x.put("productoId", p.getProductoId());
            x.put("titulo", p.getTitulo());
            x.put("variante", p.isEsPlan() ? textoDelPlan(p) : p.getVariante());
            x.put("imagen", p.getImagen());
            x.put("esPlan", p.isEsPlan());
            x.put("cantidad", p.getCantidad());
            x.put("maximo", p.isEsPlan() ? Integer.valueOf(1) : maximo == null ? Integer.valueOf(MAX_POR_PARTIDA) : Integer.valueOf(Math.min(maximo, MAX_POR_PARTIDA)));
            x.put("precioUnitario", p.getPrecioUnitario());
            x.put("total", PedidoService.centavos(p.getPrecioUnitario() * p.getCantidad()));
            items.add(x);
            articulos += p.getCantidad();
        }
        double total = total(c);
        double iva = PedidoService.centavos(total - total / 1.16);
        v.put("items", items);
        v.put("articulos", articulos);
        v.put("subtotal", PedidoService.centavos(total - iva));
        v.put("iva", iva);
        v.put("total", total);
        v.put("avisos", avisos);
        return v;
    }

    private static Map<String, Object> vistaVacia(String gymId, String canal) {
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("id", null);
        v.put("gymId", gymId);
        v.put("canal", canal);
        v.put("items", List.of());
        v.put("articulos", 0);
        v.put("subtotal", 0.0);
        v.put("iva", 0.0);
        v.put("total", 0.0);
        v.put("avisos", List.of());
        return v;
    }

    private static String textoDelPlan(Carrito.Partida p) {
        if (!PlanPagado.UNIDADES.contains(p.getDuracionUnidad()) || p.getDuracionCantidad() == null || p.getDuracionCantidad() < 1) {
            return "Pago único";
        }
        return CatalogoService.duracionTexto(new PlanPagado(null, null, p.getDuracionUnidad(), p.getDuracionCantidad()));
    }

    private static String dinero(double monto) {
        return String.format(java.util.Locale.forLanguageTag("es-MX"), "$%,.2f", monto);
    }
}
