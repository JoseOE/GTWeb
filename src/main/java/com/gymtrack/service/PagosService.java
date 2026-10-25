package com.gymtrack.service;

import com.gymtrack.model.Gym;
import com.gymtrack.model.OrdenPaypal;
import com.gymtrack.model.Pedido;
import com.gymtrack.model.SesionStripe;
import com.gymtrack.model.User;
import com.gymtrack.repository.GymRepository;
import com.gymtrack.repository.OrdenPaypalRepository;
import com.gymtrack.repository.SesionStripeRepository;
import com.gymtrack.repository.UserRepository;
import com.gymtrack.util.Fechas;
import com.gymtrack.util.MetodosPago;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// Cómo se cobra, según PAGOS_MODO:
//  - sandbox: con las APIs reales en su modo de pruebas. La tarjeta se paga en
//    la página de Stripe (Checkout), PayPal en la suya y el efectivo en tiendas
//    con una ficha de Paynet que genera Openpay. Nada de tarjetas pasa por
//    este servidor.
//  - simulado (por defecto): con los simuladores de siempre, sin llaves y sin
//    salir del servidor. Lo usan las pruebas automáticas del CI.
//
// En los dos modos el dinero se cobra aquí, ya revisado el carrito: Stripe
// queda autorizado en su página y se captura al crear el pedido; PayPal queda
// aprobado y se captura igual. Si el pedido no se puede crear, no se cobra.
@Service
public class PagosService {

    private static final Logger log = LoggerFactory.getLogger(PagosService.class);
    public static final String SANDBOX = "sandbox";
    private static final DateTimeFormatter FECHA_OPENPAY = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    // Una partida tal como se muestra en la página de pago del proveedor.
    public record Concepto(String nombre, int cantidad, double precioUnitario) {}

    private final boolean sandbox;
    private final SimuladorPagoService simuladores;
    private final SimuladorStripeService stripeSim;
    private final SimuladorPaypalService paypalSim;
    private final StripeApi stripe;
    private final PaypalApi paypal;
    private final OpenpayApi openpay;
    private final SesionStripeRepository sesiones;
    private final OrdenPaypalRepository ordenes;
    private final GymRepository gyms;
    private final UserRepository users;
    private final Regresos regresos;
    private final MongoTemplate mongo;

    public PagosService(@Value("${pagos.modo:simulado}") String modo,
                        SimuladorPagoService simuladores, SimuladorStripeService stripeSim, SimuladorPaypalService paypalSim,
                        StripeApi stripe, PaypalApi paypal, OpenpayApi openpay,
                        SesionStripeRepository sesiones, OrdenPaypalRepository ordenes, GymRepository gyms,
                        UserRepository users, Regresos regresos, MongoTemplate mongo) {
        this.sandbox = SANDBOX.equalsIgnoreCase(modo == null ? "" : modo.trim());
        this.simuladores = simuladores;
        this.stripeSim = stripeSim;
        this.paypalSim = paypalSim;
        this.stripe = stripe;
        this.paypal = paypal;
        this.openpay = openpay;
        this.sesiones = sesiones;
        this.ordenes = ordenes;
        this.gyms = gyms;
        this.users = users;
        this.regresos = regresos;
        this.mongo = mongo;
        if (sandbox) {
            log.info("Pagos en modo sandbox: Stripe {}, PayPal {}, Openpay {}.",
                    stripe.configurada() ? "listo" : "SIN LLAVES", paypal.configurada() ? "listo" : "SIN LLAVES",
                    openpay.configurada() ? "listo" : "SIN LLAVES");
        } else {
            log.info("Pagos en modo simulado (PAGOS_MODO=simulado): solo para pruebas automáticas.");
        }
    }

    public boolean sandbox() {
        return sandbox;
    }

    // ═══════════════════════════ TARJETA · STRIPE CHECKOUT ═══════════════════════════

    // Abre la página de pago de Stripe por el total. El cargo queda autorizado
    // (capture_method=manual) y se captura al crear el pedido. Devuelve {id, url}.
    public Map<String, Object> crearSesionStripe(User quien, String gymId, String canal, double total, List<Concepto> conceptos,
                                                 String returnUrl, String cancelUrl, String hostPropio) {
        if (!sandbox) {
            throw new TiendaException(HttpStatus.CONFLICT, "En modo simulado la tarjeta se paga con un token de /api/simuladores/stripe/tokens.");
        }
        if (total <= 0 || conceptos.isEmpty()) throw new TiendaException(HttpStatus.BAD_REQUEST, "No hay nada que cobrar.");
        String regreso = regresos.paraProveedor(regresos.validar(returnUrl, "returnUrl", hostPropio));
        String cancelacion = regresos.paraProveedor(regresos.validar(cancelUrl, "cancelUrl", hostPropio));
        String comercio = comercio(gymId);

        MultiValueMap<String, String> f = new LinkedMultiValueMap<>();
        f.add("mode", "payment");
        f.add("locale", "es-419");
        // El placeholder lo reemplaza Stripe: se agrega sin codificar.
        f.add("success_url", regreso + (regreso.contains("?") ? "&" : "?") + "session_id={CHECKOUT_SESSION_ID}");
        f.add("cancel_url", cancelacion);
        for (int i = 0; i < conceptos.size(); i++) {
            Concepto c = conceptos.get(i);
            String p = "line_items[" + i + "]";
            f.add(p + "[quantity]", String.valueOf(c.cantidad()));
            f.add(p + "[price_data][currency]", "mxn");
            f.add(p + "[price_data][unit_amount]", String.valueOf(centavos(c.precioUnitario())));
            f.add(p + "[price_data][product_data][name]", recortar(c.nombre(), 120));
        }
        f.add("payment_intent_data[capture_method]", "manual");
        f.add("payment_intent_data[description]", recortar("Compra en " + comercio + " (GymTrack)", 300));
        f.add("payment_intent_data[statement_descriptor_suffix]", "GYMTRACK");
        for (String p : List.of("metadata", "payment_intent_data[metadata]")) {
            f.add(p + "[userId]", quien.getId());
            f.add(p + "[gymId]", gymId);
            f.add(p + "[canal]", canal);
        }
        if (quien.getEmail() != null && quien.getEmail().contains("@") && !Pedido.CANAL_MOSTRADOR.equals(canal)) {
            f.add("customer_email", quien.getEmail());
        }
        Map<String, Object> sesion = stripe.post("/checkout/sessions", f);
        if (centavos(total) != numero(sesion.get("amount_total"))) {
            log.error("Stripe armó la sesión {} por {} centavos y el total es {}.", sesion.get("id"), sesion.get("amount_total"), centavos(total));
            throw new TiendaException(HttpStatus.BAD_GATEWAY, "No pudimos preparar el pago con tarjeta. Intenta de nuevo.");
        }

        SesionStripe s = new SesionStripe();
        s.setId(String.valueOf(sesion.get("id")));
        s.setUserId(quien.getId());
        s.setGymId(gymId);
        s.setCanal(canal);
        s.setMonto(total);
        s.setEstado(SesionStripe.CREADA);
        s.setCreadaEn(Instant.now());
        sesiones.save(s);

        Map<String, Object> v = new LinkedHashMap<>();
        v.put("id", s.getId());
        v.put("url", sesion.get("url"));
        v.put("monto", total);
        return v;
    }

    // Al volver de Stripe: revisa que la sesión sea de quien paga, de este
    // gimnasio y canal, por este total, que el cargo esté autorizado y que no se
    // haya usado. Devuelve lo que guarda el pedido para capturar después.
    @SuppressWarnings("unchecked")
    public Map<String, Object> consumirStripe(String userId, String gymId, String canal, double total, Map<String, Object> datos) {
        if (!sandbox) return stripeSim.consumir(userId, datos, total);
        Object id = datos == null ? null : datos.get("sesion");
        SesionStripe s = id == null ? null : sesiones.findById(id.toString()).orElse(null);
        if (s == null || !s.getUserId().equals(userId) || !s.getGymId().equals(gymId) || !s.getCanal().equals(canal)) {
            throw new TiendaException(HttpStatus.BAD_REQUEST, "El pago con tarjeta ya no es válido. Vuelve a intentarlo.");
        }
        if (!SesionStripe.CREADA.equals(s.getEstado())) {
            throw new TiendaException(HttpStatus.BAD_REQUEST, "Ese pago con tarjeta ya se usó. Vuelve a intentarlo.");
        }
        Map<String, Object> sesion = stripe.get("/checkout/sessions/" + s.getId() + "?expand[]=payment_intent");
        Map<String, Object> intento = sesion.get("payment_intent") instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
        String pi = String.valueOf(intento.getOrDefault("id", ""));
        if (!"complete".equals(sesion.get("status")) || !"requires_capture".equals(intento.get("status"))) {
            throw new TiendaException(HttpStatus.PAYMENT_REQUIRED, "El pago con tarjeta no se completó en Stripe. Vuelve a intentarlo.");
        }
        if (Math.abs(s.getMonto() - total) > 0.009 || numero(sesion.get("amount_total")) != centavos(total)) {
            cancelarIntento(pi);
            throw new TiendaException(HttpStatus.CONFLICT, "Tu carrito cambió desde que pagaste en Stripe. No se te cobró: vuelve a pagar el total nuevo.");
        }
        SesionStripe tomada = mongo.findAndModify(
                Query.query(Criteria.where("_id").is(s.getId()).and("estado").is(SesionStripe.CREADA)),
                new Update().set("estado", SesionStripe.USADA).set("usadaEn", Instant.now()).set("paymentIntent", pi),
                FindAndModifyOptions.options().returnNew(true), SesionStripe.class);
        if (tomada == null) {
            throw new TiendaException(HttpStatus.BAD_REQUEST, "Ese pago con tarjeta ya se usó. Vuelve a intentarlo.");
        }
        Map<String, Object> seguros = new LinkedHashMap<>();
        seguros.put("sesion", s.getId());
        seguros.put("paymentIntent", pi);
        return seguros;
    }

    // ═══════════════════════════ PAYPAL ═══════════════════════════

    // Crea la orden por el total (Orders v2) y devuelve {id, monto, moneda, aprobarUrl}.
    @SuppressWarnings("unchecked")
    public Map<String, Object> crearOrdenPaypal(User comprador, String canal, double total, int articulos,
                                                String returnUrl, String cancelUrl, String hostPropio) {
        if (!sandbox) return paypalSim.crear(comprador, canal, total, articulos, returnUrl, cancelUrl, hostPropio, false);
        if (articulos <= 0 || total <= 0) throw new TiendaException(HttpStatus.BAD_REQUEST, "Tu carrito está vacío.");
        String regreso = regresos.validar(returnUrl, "returnUrl", hostPropio);
        String cancelacion = regresos.validar(cancelUrl, "cancelUrl", hostPropio);
        String comercio = comercio(comprador.getGymId());

        Map<String, Object> contexto = new LinkedHashMap<>();
        contexto.put("brand_name", recortar(comercio, 120));
        contexto.put("locale", "es-MX");
        contexto.put("shipping_preference", "NO_SHIPPING");
        contexto.put("user_action", "PAY_NOW");
        contexto.put("return_url", regresos.paraProveedor(regreso));
        contexto.put("cancel_url", regresos.paraProveedor(cancelacion));
        Map<String, Object> unidad = new LinkedHashMap<>();
        unidad.put("reference_id", comprador.getGymId());
        unidad.put("custom_id", comprador.getId());
        unidad.put("description", recortar("Compra en " + comercio + " (GymTrack)", 120));
        unidad.put("amount", Map.of("currency_code", "MXN", "value", monto(total)));
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("intent", "CAPTURE");
        cuerpo.put("purchase_units", List.of(unidad));
        cuerpo.put("payment_source", Map.of("paypal", Map.of("experience_context", contexto)));

        Map<String, Object> r;
        try {
            r = paypal.post("/v2/checkout/orders", cuerpo, null);
        } catch (PaypalApi.ErrorPaypal e) {
            throw new TiendaException(HttpStatus.BAD_GATEWAY, "PayPal no pudo preparar el pago. Intenta de nuevo o elige otro método.");
        }
        String aprobar = null;
        if (r.get("links") instanceof List<?> links) {
            for (Object o : links) {
                if (o instanceof Map<?, ?> l && ("payer-action".equals(l.get("rel")) || "approve".equals(l.get("rel")))) {
                    aprobar = String.valueOf(l.get("href"));
                }
            }
        }
        if (aprobar == null) throw new TiendaException(HttpStatus.BAD_GATEWAY, "PayPal no devolvió la página para aprobar el pago.");

        OrdenPaypal orden = new OrdenPaypal();
        orden.setId(String.valueOf(r.get("id")));
        orden.setUserId(comprador.getId());
        orden.setGymId(comprador.getGymId());
        orden.setCanal(canal);
        orden.setComercio(comercio);
        orden.setMonto(total);
        orden.setMoneda("MXN");
        orden.setArticulos(articulos);
        orden.setEstado(OrdenPaypal.CREADA);
        orden.setReturnUrl(regreso);
        orden.setCancelUrl(cancelacion);
        orden.setCreadaEn(Instant.now());
        ordenes.save(orden);

        Map<String, Object> v = new LinkedHashMap<>();
        v.put("id", orden.getId());
        v.put("monto", total);
        v.put("moneda", "MXN");
        v.put("aprobarUrl", aprobar);
        return v;
    }

    // Al volver de PayPal: la orden debe ser de quien paga, de su gimnasio y
    // canal, por este total y sin usar. Se captura al crear el pedido.
    public Map<String, Object> consumirPaypal(String userId, String gymId, String canal, double total, Map<String, Object> datos) {
        if (!sandbox) return paypalSim.consumir(userId, gymId, canal, total, datos);
        if (datos != null && Boolean.TRUE.equals(datos.get("vinculo"))) {
            throw new TiendaException(HttpStatus.BAD_REQUEST, "La cuenta PayPal vinculada ya no está disponible. Paga con PayPal para aprobar el pago.");
        }
        Object token = datos == null ? null : datos.get("token");
        OrdenPaypal orden = token == null ? null : ordenes.findById(token.toString()).orElse(null);
        if (orden == null || !orden.getUserId().equals(userId) || !orden.getGymId().equals(gymId) || !orden.getCanal().equals(canal)) {
            throw new TiendaException(HttpStatus.BAD_REQUEST, "El pago de PayPal ya no es válido. Vuelve a intentarlo.");
        }
        if (Math.abs(orden.getMonto() - total) > 0.009) {
            throw new TiendaException(HttpStatus.CONFLICT, "Tu carrito cambió desde que aprobaste el pago en PayPal. Vuelve a pagar para aprobar el total nuevo.");
        }
        OrdenPaypal tomada = mongo.findAndModify(
                Query.query(Criteria.where("_id").is(orden.getId()).and("estado").is(OrdenPaypal.CREADA)),
                new Update().set("estado", OrdenPaypal.USADA).set("usadaEn", Instant.now()),
                FindAndModifyOptions.options().returnNew(true), OrdenPaypal.class);
        if (tomada == null) {
            throw new TiendaException(HttpStatus.BAD_REQUEST, "Ese pago de PayPal ya se usó. Vuelve a intentarlo.");
        }
        Map<String, Object> seguros = new LinkedHashMap<>();
        seguros.put("orden", orden.getId());
        if (datos.get("payerId") != null) seguros.put("payerId", datos.get("payerId").toString());
        return seguros;
    }

    // ═══════════════════════════ COBRAR ═══════════════════════════

    // Lo llama PedidoService con el inventario ya apartado: captura el cargo de
    // Stripe, la orden de PayPal o pide a Openpay la ficha de Paynet.
    public SimuladorPagoService.Resultado autorizar(PedidoService.Venta v, String orderId, double total) {
        if (!sandbox || MetodosPago.EFECTIVO.equals(v.proveedor())) {
            return simuladores.autorizar(v.proveedor(), v.datos(), total);
        }
        return switch (v.proveedor()) {
            case MetodosPago.STRIPE -> capturarStripe(v.datos(), total);
            case MetodosPago.PAYPAL -> capturarPaypal(v.datos(), total);
            case MetodosPago.PAYNET -> fichaOpenpay(v, orderId, total);
            default -> throw new TiendaException(HttpStatus.BAD_REQUEST, "Elige cómo quieres pagar.");
        };
    }

    // No se pudo crear el pedido (algo se agotó, otra sesión ganó): el cargo de
    // la tarjeta que quedó autorizado se libera para que no se cobre.
    public void liberar(String proveedor, Map<String, Object> datos) {
        if (!sandbox || datos == null || !MetodosPago.STRIPE.equals(proveedor)) return;
        Object pi = datos.get("paymentIntent");
        if (pi != null) cancelarIntento(pi.toString());
    }

    @SuppressWarnings("unchecked")
    private SimuladorPagoService.Resultado capturarStripe(Map<String, Object> datos, double total) {
        String pi = datos == null || datos.get("paymentIntent") == null ? null : datos.get("paymentIntent").toString();
        if (pi == null) throw new TiendaException(HttpStatus.PAYMENT_REQUIRED, "Falta pagar con tarjeta en Stripe. Vuelve a intentarlo.");
        MultiValueMap<String, String> f = new LinkedMultiValueMap<>();
        f.add("expand[]", "latest_charge");
        Map<String, Object> intento;
        try {
            intento = stripe.post("/payment_intents/" + pi + "/capture", f);
        } catch (StripeApi.ErrorStripe e) {
            throw new TiendaException(HttpStatus.PAYMENT_REQUIRED, "Stripe no pudo cobrar la tarjeta. Intenta con otra tarjeta u otro método.");
        }
        if (!"succeeded".equals(intento.get("status"))) {
            throw new TiendaException(HttpStatus.PAYMENT_REQUIRED, "Stripe no pudo cobrar la tarjeta. Intenta con otra tarjeta u otro método.");
        }
        Map<String, Object> cargo = intento.get("latest_charge") instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
        Map<String, Object> detalles = mapa(cargo.get("payment_method_details"));
        Map<String, Object> tarjeta = mapa(detalles.get("card"));
        Map<String, Object> facturacion = mapa(cargo.get("billing_details"));

        Map<String, Object> d = base(pi, "stripe", total);
        d.put("sesion", datos.get("sesion"));
        d.put("cargo", cargo.get("id"));
        d.put("marca", tarjeta.get("brand"));
        d.put("ultimos4", tarjeta.get("last4"));
        d.put("tipo", "debit".equals(tarjeta.get("funding")) ? SimuladorStripeService.DEBITO : SimuladorStripeService.CREDITO);
        if (tarjeta.get("exp_month") != null && tarjeta.get("exp_year") != null) {
            d.put("vencimiento", String.format("%02d/%02d", numero(tarjeta.get("exp_month")), numero(tarjeta.get("exp_year")) % 100));
        }
        if (facturacion.get("name") != null) d.put("titular", facturacion.get("name"));
        if (tarjeta.get("issuer") != null) d.put("banco", tarjeta.get("issuer"));
        d.put("autorizacion", cargo.get("authorization_code") != null ? cargo.get("authorization_code") : ultimos(String.valueOf(cargo.get("id")), 6));
        d.put("operacion", cargo.get("id"));
        Map<String, Object> plan = mapa(mapa(tarjeta.get("installments")).get("plan"));
        if (numero(plan.get("count")) > 1) {
            long meses = numero(plan.get("count"));
            d.put("meses", meses);
            d.put("mensualidad", Math.round(total / meses * 100) / 100.0);
        }
        if (cargo.get("receipt_url") != null) d.put("reciboStripe", cargo.get("receipt_url"));
        cobrado(d);
        return new SimuladorPagoService.Resultado(true, d);
    }

    @SuppressWarnings("unchecked")
    private SimuladorPagoService.Resultado capturarPaypal(Map<String, Object> datos, double total) {
        String id = datos == null || datos.get("orden") == null ? null : datos.get("orden").toString();
        if (id == null) throw new TiendaException(HttpStatus.PAYMENT_REQUIRED, "Falta aprobar el pago en PayPal. Vuelve a intentarlo.");
        Map<String, Object> r;
        try {
            r = paypal.post("/v2/checkout/orders/" + id + "/capture", Map.of(), "captura-" + id);
        } catch (PaypalApi.ErrorPaypal e) {
            if ("ORDER_NOT_APPROVED".equals(e.getMessage()) || "PAYER_ACTION_REQUIRED".equals(e.getMessage())) {
                throw new TiendaException(HttpStatus.BAD_REQUEST, "Todavía no apruebas el pago en PayPal.");
            }
            throw new TiendaException(HttpStatus.PAYMENT_REQUIRED, "PayPal rechazó el pago con esa cuenta. Intenta con otra cuenta u otro método.");
        }
        Map<String, Object> captura = Map.of();
        if (r.get("purchase_units") instanceof List<?> unidades && !unidades.isEmpty()) {
            Map<String, Object> pagos = mapa(mapa(unidades.get(0)).get("payments"));
            if (pagos.get("captures") instanceof List<?> capturas && !capturas.isEmpty()) captura = mapa(capturas.get(0));
        }
        if (!"COMPLETED".equals(r.get("status")) || !"COMPLETED".equals(captura.get("status"))) {
            throw new TiendaException(HttpStatus.PAYMENT_REQUIRED, "PayPal no completó el pago. Intenta de nuevo u otro método.");
        }
        Map<String, Object> pagador = mapa(r.get("payer"));
        Map<String, Object> nombre = mapa(pagador.get("name"));

        Map<String, Object> d = base(id, "paypal", total);
        d.put("orden", id);
        d.put("captura", captura.get("id"));
        d.put("payerId", pagador.get("payer_id"));
        d.put("cuenta", pagador.get("email_address"));
        String completo = ((nombre.getOrDefault("given_name", "") + " " + nombre.getOrDefault("surname", ""))).trim();
        if (!completo.isEmpty()) d.put("nombre", completo);
        cobrado(d);
        return new SimuladorPagoService.Resultado(true, d);
    }

    // Ficha de Paynet: Openpay genera la referencia y el código de barras. El
    // pedido queda pendiente hasta que se paga en la tienda (ver FichasService).
    @SuppressWarnings("unchecked")
    private SimuladorPagoService.Resultado fichaOpenpay(PedidoService.Venta v, String orderId, double total) {
        Instant vence = Instant.now().plus(Duration.ofHours(SimuladorPagoService.VIGENCIA_PAYNET_HORAS)).truncatedTo(ChronoUnit.SECONDS);
        User comprador = v.userId() == null ? null : users.findById(v.userId()).orElse(null);
        String nombre = comprador != null && comprador.getNombre() != null && !comprador.getNombre().isBlank() ? comprador.getNombre() : "Cliente";
        Map<String, Object> cliente = new LinkedHashMap<>();
        cliente.put("name", recortar(nombre, 100));
        if (v.email() != null && v.email().contains("@")) cliente.put("email", v.email());

        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("method", "store");
        cuerpo.put("amount", BigDecimal.valueOf(total).setScale(2, RoundingMode.HALF_UP));
        cuerpo.put("currency", "MXN");
        cuerpo.put("description", recortar("Pedido en " + comercio(v.gymId()) + " (GymTrack)", 250));
        cuerpo.put("order_id", orderId);
        cuerpo.put("due_date", LocalDateTime.ofInstant(vence, Fechas.ZONA_MX).format(FECHA_OPENPAY));
        cuerpo.put("customer", cliente);
        Map<String, Object> r = openpay.crearCargo(cuerpo);
        Map<String, Object> metodo = mapa(r.get("payment_method"));
        Object referencia = metodo.get("reference");
        if (referencia == null) throw new TiendaException(HttpStatus.BAD_GATEWAY, "Openpay no devolvió la referencia de pago. Intenta de nuevo.");

        Map<String, Object> d = base(String.valueOf(r.get("id")), "openpay", total);
        d.put("cargoOpenpay", r.get("id"));
        d.put("referencia", referencia.toString());
        if (metodo.get("barcode_url") != null) d.put("codigoBarrasUrl", metodo.get("barcode_url"));
        d.put("fichaUrl", openpay.urlFicha(referencia.toString()));
        d.put("vence", vence.toString());
        d.put("estado", "autorizado");
        d.put("autorizadoEn", ahora());
        return new SimuladorPagoService.Resultado(false, d);
    }

    // ═══════════════════════════ REEMBOLSOS ═══════════════════════════

    // Devuelve el dinero con la API del método (sandbox) y regresa el id del
    // reembolso. El efectivo (mostrador o tiendas) se devuelve en recepción.
    public String reembolsar(Pedido p) {
        Map<String, Object> d = p.getDatosPago() == null ? Map.of() : p.getDatosPago();
        if (!sandbox || d.get("modo") == null) return SimuladorPagoService.nuevoId("re_sim");
        switch (p.getProveedorPago()) {
            case MetodosPago.STRIPE -> {
                MultiValueMap<String, String> f = new LinkedMultiValueMap<>();
                f.add("payment_intent", String.valueOf(d.get("id")));
                try {
                    return String.valueOf(stripe.post("/refunds", f).get("id"));
                } catch (StripeApi.ErrorStripe e) {
                    throw new TiendaException(HttpStatus.BAD_GATEWAY, "Stripe no pudo reembolsar el pago: " + e.getMessage());
                }
            }
            case MetodosPago.PAYPAL -> {
                try {
                    return String.valueOf(paypal.post("/v2/payments/captures/" + d.get("captura") + "/refund", Map.of(), "reembolso-" + p.getOrderId()).get("id"));
                } catch (PaypalApi.ErrorPaypal e) {
                    throw new TiendaException(HttpStatus.BAD_GATEWAY, "PayPal no pudo reembolsar el pago (" + e.getMessage() + ").");
                }
            }
            default -> {
                return "re_caja_" + Instant.now().toEpochMilli();
            }
        }
    }

    // ═══════════════════════════ AYUDANTES ═══════════════════════════

    private void cancelarIntento(String pi) {
        if (pi == null || pi.isBlank()) return;
        try {
            stripe.post("/payment_intents/" + pi + "/cancel", null);
        } catch (RuntimeException e) {
            log.warn("No se pudo liberar el cargo {} de Stripe: {}", pi, e.getMessage());
        }
    }

    private String comercio(String gymId) {
        return gymId == null ? "GymTrack" : gyms.findById(gymId).map(Gym::getNombre).filter(n -> n != null && !n.isBlank()).orElse("GymTrack");
    }

    private static Map<String, Object> base(String id, String proveedor, double monto) {
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("id", id);
        d.put("proveedor", proveedor);
        d.put("modo", SANDBOX);
        d.put("monto", monto);
        d.put("moneda", TiendaGymService.MONEDA);
        return d;
    }

    private static void cobrado(Map<String, Object> d) {
        String ahora = ahora();
        d.put("estado", "capturado");
        d.put("autorizadoEn", ahora);
        d.put("capturadoEn", ahora);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> mapa(Object o) {
        return o instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
    }

    private static long numero(Object o) {
        if (o instanceof Number n) return n.longValue();
        try {
            return o == null ? 0 : Long.parseLong(o.toString());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    static long centavos(double pesos) {
        return BigDecimal.valueOf(pesos).multiply(BigDecimal.valueOf(100)).setScale(0, RoundingMode.HALF_UP).longValue();
    }

    private static String monto(double pesos) {
        return BigDecimal.valueOf(pesos).setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private static String recortar(String s, int max) {
        return s == null ? "" : (s.length() <= max ? s : s.substring(0, max));
    }

    private static String ultimos(String s, int n) {
        return s == null ? "" : s.length() <= n ? s : s.substring(s.length() - n);
    }

    private static String ahora() {
        return Instant.now().truncatedTo(ChronoUnit.MILLIS).toString();
    }
}
