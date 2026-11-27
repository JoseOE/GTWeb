package com.gymtrack.service;

import com.gymtrack.model.Gym;
import com.gymtrack.model.PagoSuscripcion;
import com.gymtrack.model.PreciosSuscripcion;
import com.gymtrack.model.User;
import com.gymtrack.repository.GymRepository;
import com.gymtrack.repository.PagoSuscripcionRepository;
import com.gymtrack.repository.UserRepository;
import com.gymtrack.util.Fechas;
import com.gymtrack.util.MetodosPago;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.mongodb.client.model.UpdateOptions;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

// Lo que cada gimnasio le paga a GymTrack.
//
//  - Plan anual: cubre hasta el 31 de diciembre. Plan mensual: hasta el último
//    día del mes. Así todos vencen el mismo día y renuevan juntos.
//  - Quien entra a medio periodo paga solo los días que faltan (proporcional).
//    Si faltan muy pocos (menos de 31 días del año o de 5 del mes), el pago
//    cubre también el periodo siguiente completo, para no cobrar dos veces seguidas.
//  - Estados: AL_DIA · POR_VENCER (30 días o menos) · GRACIA (vencida, con
//    diasGracia para pagar) · VENCIDA (el panel queda en solo lectura:
//    SuscripcionInterceptor rechaza los cambios). La puerta, la tienda y la app
//    de los socios siguen funcionando: ellos ya pagaron su membresía.
//  - Los gimnasios que existían antes de cobrar quedan con cortesía hasta el
//    31 dic 2026. Un gimnasio nuevo empieza sin pagar y tiene los días de
//    gracia para activar su suscripción.
@Service
public class SuscripcionService {

    private static final Logger log = LoggerFactory.getLogger(SuscripcionService.class);

    public static final String ANUAL = "anual";
    public static final String MENSUAL = "mensual";
    public static final String CORTESIA = "cortesia";
    public static final String AL_DIA = "AL_DIA";
    public static final String POR_VENCER = "POR_VENCER";
    public static final String GRACIA = "GRACIA";
    public static final String VENCIDA = "VENCIDA";

    static final int AVISAR_DESDE_DIAS = 30;
    // Se puede renovar cuando falta esto o menos para vencer.
    static final int RENOVAR_DESDE_DIAS = 45;
    static final int MINIMO_DIAS_ANUAL = 31;
    static final int MINIMO_DIAS_MENSUAL = 5;
    static final LocalDate FIN_CORTESIA = LocalDate.of(2026, 12, 31);
    private static final String MARCA_CORTESIA = "suscripciones-cortesia-2026";
    // Recordatorios por correo: estos días antes de vencer.
    static final List<Integer> DIAS_RECORDATORIO = List.of(30, 7, 1);

    private final GymRepository gyms;
    private final UserRepository users;
    private final PagoSuscripcionRepository pagos;
    private final PagosService cobros;
    private final CorreoService correos;
    private final MongoTemplate mongo;

    public SuscripcionService(GymRepository gyms, UserRepository users, PagoSuscripcionRepository pagos,
                              PagosService cobros, CorreoService correos, MongoTemplate mongo) {
        this.gyms = gyms;
        this.users = users;
        this.pagos = pagos;
        this.cobros = cobros;
        this.correos = correos;
        this.mongo = mongo;
    }

    // ─────────────────────────── Precios ───────────────────────────

    public PreciosSuscripcion precios() {
        PreciosSuscripcion p = mongo.findById(PreciosSuscripcion.ID, PreciosSuscripcion.class);
        return p == null ? new PreciosSuscripcion() : p;
    }

    public PreciosSuscripcion cambiarPrecios(Double anual, Double mensual, Integer diasGracia) {
        if (anual == null || mensual == null || anual < 1 || mensual < 1 || anual > 1_000_000 || mensual > 100_000) {
            throw new TiendaException(HttpStatus.BAD_REQUEST, "Escribe el precio anual y el mensual en pesos (mayores a $0).");
        }
        if (diasGracia == null || diasGracia < 0 || diasGracia > 60) {
            throw new TiendaException(HttpStatus.BAD_REQUEST, "Los días de gracia van de 0 a 60.");
        }
        PreciosSuscripcion p = precios();
        p.setAnual(redondear(anual));
        p.setMensual(redondear(mensual));
        p.setDiasGracia(diasGracia);
        p.setActualizadoEn(Instant.now());
        return mongo.save(p);
    }

    // ─────────────────────────── Estado de un gimnasio ───────────────────────────

    // Hasta qué día está pagado. Un gimnasio nuevo que no ha pagado cuenta
    // como vencido el día antes de crearse.
    public static LocalDate hasta(Gym g) {
        if (g.getSuscripcionHasta() != null) return g.getSuscripcionHasta();
        return creado(g).minusDays(1);
    }

    public Map<String, Object> estado(Gym g) {
        return estado(g, precios(), Fechas.hoy());
    }

    Map<String, Object> estado(Gym g, PreciosSuscripcion precios, LocalDate hoy) {
        LocalDate hasta = hasta(g);
        long dias = ChronoUnit.DAYS.between(hoy, hasta);
        LocalDate soloLecturaDesde = hasta.plusDays(precios.getDiasGracia() + 1L);
        String estado = dias > AVISAR_DESDE_DIAS ? AL_DIA
                : dias >= 0 ? POR_VENCER
                : hoy.isBefore(soloLecturaDesde) ? GRACIA
                : VENCIDA;
        Map<String, Object> e = new LinkedHashMap<>();
        e.put("estado", estado);
        e.put("plan", g.getSuscripcionPlan());
        e.put("hasta", hasta);
        e.put("nuncaPagada", g.getSuscripcionHasta() == null);
        e.put("diasRestantes", dias);
        e.put("soloLectura", VENCIDA.equals(estado));
        e.put("soloLecturaDesde", soloLecturaDesde);
        e.put("puedeRenovar", dias <= RENOVAR_DESDE_DIAS);
        return e;
    }

    public boolean soloLectura(String gymId) {
        return gyms.findById(gymId).map(g -> Boolean.TRUE.equals(estado(g).get("soloLectura"))).orElse(false);
    }

    // Lo que ve el dueño: su estado, lo que pagaría con cada plan y sus pagos.
    public Map<String, Object> vistaDueno(Gym g) {
        PreciosSuscripcion precios = precios();
        LocalDate hoy = Fechas.hoy();
        Map<String, Object> v = estado(g, precios, hoy);
        v.put("precios", vistaPrecios(precios));
        Map<String, Object> cotizaciones = new LinkedHashMap<>();
        cotizaciones.put(ANUAL, vistaCotizacion(cotizar(g, ANUAL, precios, hoy)));
        cotizaciones.put(MENSUAL, vistaCotizacion(cotizar(g, MENSUAL, precios, hoy)));
        v.put("cotizaciones", cotizaciones);
        v.put("pagos", pagos.findByGymIdOrderByPagadoEnDesc(g.getId()).stream().map(SuscripcionService::vistaPago).toList());
        return v;
    }

    // ─────────────────────────── Cuánto se cobra ───────────────────────────

    public record Cotizacion(String plan, LocalDate desde, LocalDate hasta, double monto, boolean completo) {}

    public Cotizacion cotizar(Gym g, String plan) {
        return cotizar(g, plan, precios(), Fechas.hoy());
    }

    // El periodo empieza hoy (si ya venció) o al día siguiente de lo pagado.
    static Cotizacion cotizar(Gym g, String plan, PreciosSuscripcion precios, LocalDate hoy) {
        String elegido = plan(plan);
        LocalDate desde = hasta(g).plusDays(1);
        if (desde.isBefore(hoy)) desde = hoy;
        LocalDate fin;
        double monto = 0;
        if (ANUAL.equals(elegido)) {
            fin = LocalDate.of(desde.getYear(), 12, 31);
            if (ChronoUnit.DAYS.between(desde, fin) + 1 < MINIMO_DIAS_ANUAL) fin = fin.plusYears(1);
            for (LocalDate inicio = desde; !inicio.isAfter(fin); inicio = LocalDate.of(inicio.getYear() + 1, 1, 1)) {
                LocalDate finTramo = LocalDate.of(inicio.getYear(), 12, 31);
                long dias = ChronoUnit.DAYS.between(inicio, finTramo) + 1;
                monto += precios.getAnual() * dias / inicio.lengthOfYear();
            }
        } else {
            fin = desde.withDayOfMonth(desde.lengthOfMonth());
            if (ChronoUnit.DAYS.between(desde, fin) + 1 < MINIMO_DIAS_MENSUAL) {
                LocalDate siguiente = desde.plusMonths(1);
                fin = siguiente.withDayOfMonth(siguiente.lengthOfMonth());
            }
            for (LocalDate inicio = desde; !inicio.isAfter(fin); inicio = inicio.withDayOfMonth(1).plusMonths(1)) {
                LocalDate finTramo = inicio.withDayOfMonth(inicio.lengthOfMonth());
                long dias = ChronoUnit.DAYS.between(inicio, finTramo) + 1;
                monto += precios.getMensual() * dias / inicio.lengthOfMonth();
            }
        }
        boolean completo = ANUAL.equals(elegido)
                ? desde.getDayOfYear() == 1 && fin.getYear() == desde.getYear()
                : desde.getDayOfMonth() == 1 && fin.getMonth() == desde.getMonth() && fin.getYear() == desde.getYear();
        return new Cotizacion(elegido, desde, fin, redondear(monto), completo);
    }

    // ─────────────────────────── Pagar en línea ───────────────────────────

    // Abre la página de Stripe (modo de prueba) por la cotización del plan.
    public Map<String, Object> sesionTarjeta(User dueno, Gym g, String plan, String returnUrl, String cancelUrl, String host) {
        Cotizacion c = cotizarParaPagar(g, plan);
        return cobros.crearSesionStripe(dueno, g.getId(), PagosService.CANAL_SUSCRIPCION, c.monto(),
                List.of(new PagosService.Concepto(concepto(c), 1, c.monto())), returnUrl, cancelUrl, host);
    }

    public Map<String, Object> ordenPaypal(User dueno, Gym g, String plan, String returnUrl, String cancelUrl, String host) {
        Cotizacion c = cotizarParaPagar(g, plan);
        return cobros.crearOrdenPaypal(dueno, g.getId(), PagosService.CANAL_SUSCRIPCION, c.monto(), 1, returnUrl, cancelUrl, host);
    }

    // metodo: "tarjeta" o "paypal"; datos: lo que dejó la página de pago
    // ({sesion} de Stripe o {token} del simulador; {token, payerId} de PayPal).
    public Map<String, Object> pagar(User dueno, Gym g, String plan, String metodo, Map<String, Object> datos) {
        Cotizacion c = cotizarParaPagar(g, plan);
        String proveedor = "paypal".equals(metodo) ? MetodosPago.PAYPAL
                : "tarjeta".equals(metodo) ? MetodosPago.STRIPE : null;
        if (proveedor == null) throw new TiendaException(HttpStatus.BAD_REQUEST, "Elige pagar con tarjeta o con PayPal.");

        Map<String, Object> seguros = MetodosPago.STRIPE.equals(proveedor)
                ? cobros.consumirStripe(dueno.getId(), g.getId(), PagosService.CANAL_SUSCRIPCION, c.monto(), datos)
                : cobros.consumirPaypal(dueno.getId(), g.getId(), PagosService.CANAL_SUSCRIPCION, c.monto(), datos);
        SimuladorPagoService.Resultado r;
        try {
            r = cobros.cobrarSuscripcion(proveedor, seguros, c.monto());
        } catch (RuntimeException e) {
            cobros.liberar(proveedor, seguros);
            throw e;
        }

        PagoSuscripcion p = new PagoSuscripcion();
        p.setGymId(g.getId());
        p.setGymNombre(g.getNombre());
        p.setUserId(dueno.getId());
        p.setPlan(c.plan());
        p.setMonto(c.monto());
        p.setDesde(c.desde());
        p.setHasta(c.hasta());
        p.setMetodo(MetodosPago.STRIPE.equals(proveedor) ? "tarjeta" : "paypal");
        p.setDatos(resumenCobro(r.datos()));
        p.setPagadoEn(Instant.now());
        p = pagos.save(p);

        // Solo avanza: dos pagos al mismo tiempo no se pisan.
        mongo.updateFirst(Query.query(Criteria.where("_id").is(g.getId())
                        .orOperator(Criteria.where("suscripcionHasta").is(null), Criteria.where("suscripcionHasta").lt(c.hasta()))),
                new Update().set("suscripcionHasta", c.hasta()).set("suscripcionPlan", c.plan()), Gym.class);
        g.setSuscripcionHasta(c.hasta());
        g.setSuscripcionPlan(c.plan());
        correos.suscripcionPagada(dueno, g.getNombre(), etiquetaPlan(c.plan()), c.monto(), c.desde(), c.hasta());

        Map<String, Object> v = vistaDueno(gyms.findById(g.getId()).orElse(g));
        v.put("pago", vistaPago(p));
        return v;
    }

    private Cotizacion cotizarParaPagar(Gym g, String plan) {
        Map<String, Object> e = estado(g);
        if (!Boolean.TRUE.equals(e.get("puedeRenovar"))) {
            throw new TiendaException(HttpStatus.CONFLICT, "Tu suscripción está pagada hasta el " + fechaLarga(hasta(g))
                    + ". Podrás renovarla " + RENOVAR_DESDE_DIAS + " días antes de que venza.");
        }
        return cotizar(g, plan);
    }

    // ─────────────────────────── Panel del equipo ───────────────────────────

    public Map<String, Object> vistaEquipo() {
        PreciosSuscripcion precios = precios();
        LocalDate hoy = Fechas.hoy();
        Map<String, User> duenos = new LinkedHashMap<>();
        List<Map<String, Object>> lista = new ArrayList<>();
        Map<String, Integer> conteo = new LinkedHashMap<>();
        for (String s : List.of(AL_DIA, POR_VENCER, GRACIA, VENCIDA)) conteo.put(s, 0);
        for (Gym g : gyms.findAll()) {
            Map<String, Object> e = estado(g, precios, hoy);
            conteo.merge((String) e.get("estado"), 1, Integer::sum);
            User d = g.getOwnerId() == null ? null : duenos.computeIfAbsent(g.getOwnerId(), id -> users.findById(id).orElse(null));
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", g.getId());
            m.put("nombre", g.getNombre());
            m.put("logo", g.getLogo());
            m.put("dueno", d == null ? null : Map.of("nombre", nombre(d), "email", d.getEmail() == null ? "" : d.getEmail()));
            m.putAll(e);
            lista.add(m);
        }
        // Lo que pide atención primero: vencidas, en gracia, por vencer.
        List<String> orden = List.of(VENCIDA, GRACIA, POR_VENCER, AL_DIA);
        lista.sort((a, b) -> {
            int c = Integer.compare(orden.indexOf(a.get("estado")), orden.indexOf(b.get("estado")));
            return c != 0 ? c : ((LocalDate) a.get("hasta")).compareTo((LocalDate) b.get("hasta"));
        });

        Instant inicioAnio = LocalDate.of(hoy.getYear(), 1, 1).atStartOfDay(Fechas.ZONA_MX).toInstant();
        Instant inicioMes = hoy.withDayOfMonth(1).atStartOfDay(Fechas.ZONA_MX).toInstant();
        List<PagoSuscripcion> delAnio = pagos.findByPagadoEnGreaterThanEqual(inicioAnio);
        double anio = delAnio.stream().mapToDouble(PagoSuscripcion::getMonto).sum();
        double mes = delAnio.stream().filter(p -> !p.getPagadoEn().isBefore(inicioMes)).mapToDouble(PagoSuscripcion::getMonto).sum();
        // Lo que falta por cobrar: renovar al año siguiente a los que vencen este año.
        long porRenovar = lista.stream().filter(m -> !((LocalDate) m.get("hasta")).isAfter(LocalDate.of(hoy.getYear(), 12, 31))).count();

        Map<String, Object> r = new LinkedHashMap<>();
        r.put("precios", vistaPrecios(precios));
        r.put("conteo", conteo);
        r.put("cobradoAnio", redondear(anio));
        r.put("cobradoMes", redondear(mes));
        r.put("pagosAnio", delAnio.size());
        r.put("porRenovar", porRenovar);
        r.put("porCobrarAnual", redondear(porRenovar * precios.getAnual()));
        r.put("gimnasios", lista);
        r.put("pagos", pagos.findTop50ByOrderByPagadoEnDesc().stream().map(SuscripcionService::vistaPago).toList());
        return r;
    }

    // Días sin cobro (demos, acuerdos): extiende lo pagado hasta esa fecha.
    public Map<String, Object> darCortesia(String gymId, LocalDate hastaNueva) {
        Gym g = gyms.findById(gymId == null ? "" : gymId)
                .orElseThrow(() -> new TiendaException(HttpStatus.NOT_FOUND, "Ese gimnasio no existe."));
        LocalDate hoy = Fechas.hoy();
        if (hastaNueva == null || hastaNueva.isBefore(hoy) || hastaNueva.isAfter(hoy.plusYears(2))) {
            throw new TiendaException(HttpStatus.BAD_REQUEST, "Elige una fecha entre hoy y dentro de dos años.");
        }
        if (!hastaNueva.isAfter(hasta(g))) {
            throw new TiendaException(HttpStatus.CONFLICT, "Ya está cubierto hasta el " + fechaLarga(hasta(g)) + ".");
        }
        g.setSuscripcionHasta(hastaNueva);
        g.setSuscripcionPlan(CORTESIA);
        gyms.save(g);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", g.getId());
        m.put("nombre", g.getNombre());
        m.putAll(estado(g));
        return m;
    }

    // ─────────────────────────── Cortesía de arranque y recordatorios ───────────────────────────

    // Una sola vez: los gimnasios que ya existían quedan pagados hasta el 31 dic 2026.
    @EventListener(ApplicationReadyEvent.class)
    public void cortesiaInicial() {
        var contadores = mongo.getCollection("contadores");
        if (contadores.find(new Document("_id", MARCA_CORTESIA)).first() != null) return;
        var r = mongo.updateMulti(Query.query(Criteria.where("suscripcionHasta").is(null)),
                new Update().set("suscripcionHasta", FIN_CORTESIA).set("suscripcionPlan", CORTESIA), Gym.class);
        contadores.updateOne(new Document("_id", MARCA_CORTESIA),
                new Document("$set", new Document("hecho", true).append("en", new java.util.Date())), new UpdateOptions().upsert(true));
        if (r.getModifiedCount() > 0) {
            log.info("Suscripciones: {} gimnasios quedan con cortesía hasta el {}.", r.getModifiedCount(), FIN_CORTESIA);
        }
    }

    // Cada mañana: correo al dueño 30, 7 y 1 días antes de vencer, y el día que
    // su panel pasa a solo lectura. Cada aviso sale una vez por fecha de vencimiento.
    @Scheduled(cron = "0 0 9 * * *", zone = "America/Mexico_City")
    public void recordatorios() {
        PreciosSuscripcion precios = precios();
        LocalDate hoy = Fechas.hoy();
        for (Gym g : gyms.findAll()) {
            if (g.getSuscripcionHasta() == null || g.getOwnerId() == null) continue;
            LocalDate hasta = g.getSuscripcionHasta();
            long dias = ChronoUnit.DAYS.between(hoy, hasta);
            String clave = null;
            if (DIAS_RECORDATORIO.contains((int) dias)) clave = hasta + ":" + dias;
            else if (hoy.equals(hasta.plusDays(precios.getDiasGracia() + 1L))) clave = hasta + ":solo-lectura";
            if (clave == null || g.getSuscripcionAvisos().contains(clave)) continue;
            User dueno = users.findById(g.getOwnerId()).orElse(null);
            if (dueno == null) continue;
            if (clave.endsWith("solo-lectura")) correos.suscripcionVencida(dueno, g.getNombre(), hasta);
            else correos.suscripcionPorVencer(dueno, g.getNombre(), hasta, dias);
            mongo.updateFirst(Query.query(Criteria.where("_id").is(g.getId())), new Update().push("suscripcionAvisos", clave), Gym.class);
        }
    }

    // ─────────────────────────── Ayudantes ───────────────────────────

    static String plan(String plan) {
        String p = plan == null ? "" : plan.trim().toLowerCase();
        if (!ANUAL.equals(p) && !MENSUAL.equals(p)) {
            throw new TiendaException(HttpStatus.BAD_REQUEST, "Elige el plan anual o el mensual.");
        }
        return p;
    }

    static String etiquetaPlan(String plan) {
        return ANUAL.equals(plan) ? "Anual" : MENSUAL.equals(plan) ? "Mensual" : CORTESIA.equals(plan) ? "Cortesía" : "Sin plan";
    }

    private static String concepto(Cotizacion c) {
        return "Suscripción GymTrack " + etiquetaPlan(c.plan()).toLowerCase() + " · del " + fechaLarga(c.desde()) + " al " + fechaLarga(c.hasta());
    }

    static String fechaLarga(LocalDate d) {
        return d.getDayOfMonth() + " " + List.of("ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "sep", "oct", "nov", "dic")
                .get(d.getMonthValue() - 1) + " " + d.getYear();
    }

    private static Map<String, Object> vistaPrecios(PreciosSuscripcion p) {
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("anual", p.getAnual());
        v.put("mensual", p.getMensual());
        v.put("diasGracia", p.getDiasGracia());
        // Cuánto se ahorra al año pagando el anual contra 12 mensualidades.
        v.put("ahorroAnual", redondear(Math.max(0, p.getMensual() * 12 - p.getAnual())));
        return v;
    }

    private static Map<String, Object> vistaCotizacion(Cotizacion c) {
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("plan", c.plan());
        v.put("desde", c.desde());
        v.put("hasta", c.hasta());
        v.put("monto", c.monto());
        v.put("proporcional", !c.completo());
        return v;
    }

    private static Map<String, Object> vistaPago(PagoSuscripcion p) {
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("id", p.getId());
        v.put("gymId", p.getGymId());
        v.put("gimnasio", p.getGymNombre());
        v.put("plan", p.getPlan());
        v.put("monto", p.getMonto());
        v.put("desde", p.getDesde());
        v.put("hasta", p.getHasta());
        v.put("metodo", p.getMetodo());
        v.put("detalle", p.getDatos());
        v.put("pagadoEn", p.getPagadoEn());
        return v;
    }

    // Lo que se guarda del cobro: suficiente para el recibo, nada sensible.
    private static Map<String, Object> resumenCobro(Map<String, Object> datos) {
        Map<String, Object> d = new LinkedHashMap<>();
        if (datos == null) return d;
        for (String k : Set.of("marca", "ultimos4", "tipo", "cuenta", "autorizacion", "operacion", "captura", "cargo")) {
            if (datos.get(k) != null) d.put(k, datos.get(k));
        }
        return d;
    }

    private static String nombre(User u) {
        return u.getNombre() == null || u.getNombre().isBlank() ? "(sin nombre)" : u.getNombre();
    }

    static LocalDate creado(Gym g) {
        try {
            return Instant.ofEpochSecond(Long.parseLong(g.getId().substring(0, 8), 16)).atZone(Fechas.ZONA_MX).toLocalDate();
        } catch (RuntimeException e) {
            return Fechas.hoy();
        }
    }

    static double redondear(double n) {
        return Math.round(n * 100) / 100.0;
    }

}
