package com.gymtrack.service;

import com.gymtrack.model.Gym;
import com.gymtrack.model.OrdenPaypal;
import com.gymtrack.model.User;
import com.gymtrack.repository.GymRepository;
import com.gymtrack.repository.OrdenPaypalRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

// Simulador de PayPal: hace las veces de la API de órdenes de PayPal.
//
//   1. El checkout crea una orden por el total del carrito (crear) y manda al
//      comprador a paypal-sim.html?token=PAYID-SIM-...
//   2. Ahí elige una cuenta de prueba (o escribe un correo) y aprueba o cancela.
//      No se pide contraseña: es una simulación.
//   3. Vuelve a la dirección que dio el checkout (una página de esta web o un
//      enlace de la app) con token y PayerID, y el checkout paga: consumir()
//      revisa la orden y la cambia por los datos con que SimuladorPagoService
//      autoriza y captura el pago.
@Service
public class SimuladorPaypalService {

    public static final String APROBADA = "aprobada";
    public static final String RECHAZADA = "rechazada";
    private static final Duration VIGENCIA = Duration.ofHours(3);
    private static final String LETRAS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final SecureRandom AZAR = new SecureRandom();
    private static final Set<String> ESQUEMAS_PROHIBIDOS = Set.of("javascript", "data", "vbscript", "file", "blob", "about");

    // Cuentas que ofrece paypal-sim.html. Cualquier otro correo también aprueba;
    // la cuenta sin saldo aprueba, pero PayPal rechaza el cobro (como una
    // tarjeta de prueba de rechazo).
    public record CuentaDePrueba(String correo, String nombre, String resultado) {}

    public static final List<CuentaDePrueba> CUENTAS = List.of(
            new CuentaDePrueba("ana.compradora@sim-paypal.test", "Ana Compradora", APROBADA),
            new CuentaDePrueba("luis.prueba@sim-paypal.test", "Luis Prueba", APROBADA),
            new CuentaDePrueba("sin-saldo@sim-paypal.test", "Cuenta sin saldo (simula un rechazo)", RECHAZADA));

    private final OrdenPaypalRepository ordenes;
    private final GymRepository gymRepository;
    private final String urlPublica;

    public SimuladorPaypalService(OrdenPaypalRepository ordenes, GymRepository gymRepository,
                                  @Value("${app.url-publica:http://localhost:8080}") String urlPublica) {
        this.ordenes = ordenes;
        this.gymRepository = gymRepository;
        this.urlPublica = urlPublica.replaceAll("/+$", "");
    }

    // Crea la orden por el total del carrito. hostPropio es el dominio por el
    // que llegó la petición: junto con APP_URL, son los únicos sitios web a los
    // que se puede volver.
    public Map<String, Object> crear(User comprador, String canal, double total, int articulos,
                                     String returnUrl, String cancelUrl, String hostPropio) {
        if (articulos <= 0 || total <= 0) {
            throw new TiendaException(HttpStatus.BAD_REQUEST, "Tu carrito está vacío.");
        }
        OrdenPaypal orden = new OrdenPaypal();
        orden.setId("PAYID-SIM-" + alAzar(16));
        orden.setUserId(comprador.getId());
        orden.setGymId(comprador.getGymId());
        orden.setCanal(canal);
        orden.setComercio(gymRepository.findById(comprador.getGymId()).map(Gym::getNombre).orElse("GymTrack"));
        orden.setMonto(total);
        orden.setMoneda("MXN");
        orden.setArticulos(articulos);
        orden.setEstado(OrdenPaypal.CREADA);
        orden.setReturnUrl(validarRegreso(returnUrl, "returnUrl", hostPropio));
        orden.setCancelUrl(validarRegreso(cancelUrl, "cancelUrl", hostPropio));
        orden.setCreadaEn(Instant.now());
        ordenes.save(orden);

        Map<String, Object> v = new LinkedHashMap<>();
        v.put("id", orden.getId());
        v.put("monto", orden.getMonto());
        v.put("moneda", orden.getMoneda());
        // Para la app, que la abre en un navegador; la web usa la ruta relativa.
        v.put("aprobarUrl", urlPublica + "/paypal-sim.html?token=" + orden.getId());
        return v;
    }

    // Lo que muestra paypal-sim.html. Sin sesión: el id de la orden es la llave.
    public Map<String, Object> ver(String id) {
        OrdenPaypal orden = exigir(id);
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("id", orden.getId());
        v.put("estado", orden.getEstado());
        v.put("comercio", orden.getComercio());
        v.put("monto", orden.getMonto());
        v.put("moneda", orden.getMoneda());
        v.put("articulos", orden.getArticulos());
        v.put("vence", orden.getCreadaEn().plus(VIGENCIA));
        v.put("cuenta", orden.getCuenta());
        v.put("cuentas", CUENTAS.stream().map(c -> Map.of("correo", c.correo(), "nombre", c.nombre())).toList());
        // Si ya se decidió (p. ej. cerró la pestaña antes de volver), por dónde seguir.
        v.put("regreso", switch (orden.getEstado()) {
            case OrdenPaypal.APROBADA -> regresoAprobado(orden);
            case OrdenPaypal.CANCELADA -> regresoCancelado(orden);
            default -> null;
        });
        return v;
    }

    public Map<String, Object> aprobar(String id, String correo) {
        OrdenPaypal orden = exigir(id);
        String cuenta = correo == null ? "" : correo.trim().toLowerCase(Locale.ROOT);
        if (OrdenPaypal.APROBADA.equals(orden.getEstado()) && cuenta.equals(orden.getCuenta())) {
            return Map.of("estado", orden.getEstado(), "redirect", regresoAprobado(orden));
        }
        exigirCreada(orden);
        if (cuenta.length() > 120 || !cuenta.matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+")) {
            throw new TiendaException(HttpStatus.BAD_REQUEST, "Escribe el correo de una cuenta de prueba.");
        }
        orden.setCuenta(cuenta);
        orden.setPayerId(alAzar(13));
        orden.setResultado(CUENTAS.stream().filter(c -> c.correo().equals(cuenta))
                .map(CuentaDePrueba::resultado).findFirst().orElse(APROBADA));
        orden.setEstado(OrdenPaypal.APROBADA);
        orden.setAprobadaEn(Instant.now());
        ordenes.save(orden);
        return Map.of("estado", orden.getEstado(), "redirect", regresoAprobado(orden));
    }

    public Map<String, Object> cancelar(String id) {
        OrdenPaypal orden = exigir(id);
        if (!OrdenPaypal.CANCELADA.equals(orden.getEstado())) {
            exigirCreada(orden);
            orden.setEstado(OrdenPaypal.CANCELADA);
            ordenes.save(orden);
        }
        return Map.of("estado", orden.getEstado(), "redirect", regresoCancelado(orden));
    }

    // Al pagar: la orden debe ser de quien paga, de ese gimnasio y canal, estar
    // aprobada y ser por el total de hoy. Se usa una sola vez. Lo que devuelve
    // sustituye a lo que mandó la página, así el resultado no se puede inventar
    // desde el navegador.
    public Map<String, Object> consumir(String userId, String gymId, String canal, double total, Map<String, Object> datos) {
        Object token = datos == null ? null : datos.get("token");
        Object payerId = datos == null ? null : datos.get("payerId");
        OrdenPaypal orden = token == null ? null : ordenes.findById(token.toString()).orElse(null);
        if (orden == null || !orden.getUserId().equals(userId) || !orden.getGymId().equals(gymId) || !orden.getCanal().equals(canal)) {
            throw new TiendaException(HttpStatus.BAD_REQUEST, "El pago de PayPal ya no es válido. Vuelve a intentarlo.");
        }
        switch (orden.getEstado()) {
            case OrdenPaypal.CREADA -> throw new TiendaException(HttpStatus.BAD_REQUEST, "Todavía no apruebas el pago en PayPal.");
            case OrdenPaypal.CANCELADA -> throw new TiendaException(HttpStatus.BAD_REQUEST, "Cancelaste el pago en PayPal. Vuelve a intentarlo o elige otro método.");
            case OrdenPaypal.USADA -> throw new TiendaException(HttpStatus.BAD_REQUEST, "Ese pago de PayPal ya se usó. Vuelve a intentarlo.");
            default -> { }
        }
        if (payerId == null || !payerId.toString().equals(orden.getPayerId())) {
            throw new TiendaException(HttpStatus.BAD_REQUEST, "El pago de PayPal ya no es válido. Vuelve a intentarlo.");
        }
        if (Math.abs(orden.getMonto() - total) > 0.009) {
            throw new TiendaException(HttpStatus.CONFLICT, "Tu carrito cambió desde que aprobaste el pago en PayPal. Vuelve a pagar para aprobar el total nuevo.");
        }
        orden.setEstado(OrdenPaypal.USADA);
        orden.setUsadaEn(Instant.now());
        ordenes.save(orden);

        Map<String, Object> seguros = new LinkedHashMap<>();
        seguros.put("orden", orden.getId());
        seguros.put("payerId", orden.getPayerId());
        seguros.put("cuenta", orden.getCuenta());
        seguros.put("resultado", orden.getResultado());
        return seguros;
    }

    private OrdenPaypal exigir(String id) {
        return ordenes.findById(id == null ? "" : id)
                .orElseThrow(() -> new TiendaException(HttpStatus.NOT_FOUND, "Este pago de PayPal ya no existe o venció. Vuelve a la tienda e inténtalo de nuevo."));
    }

    private static void exigirCreada(OrdenPaypal orden) {
        switch (orden.getEstado()) {
            case OrdenPaypal.APROBADA -> throw new TiendaException(HttpStatus.CONFLICT, "Ya aprobaste este pago.");
            case OrdenPaypal.CANCELADA -> throw new TiendaException(HttpStatus.CONFLICT, "Cancelaste este pago. Vuelve a la tienda para intentarlo de nuevo.");
            case OrdenPaypal.USADA -> throw new TiendaException(HttpStatus.CONFLICT, "Este pago ya se completó.");
            default -> { }
        }
    }

    private static String regresoAprobado(OrdenPaypal orden) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("token", orden.getId());
        params.put("PayerID", orden.getPayerId());
        return conParametros(orden.getReturnUrl(), params);
    }

    private static String regresoCancelado(OrdenPaypal orden) {
        return conParametros(orden.getCancelUrl(), Map.of("token", orden.getId()));
    }

    // A dónde se puede volver: una ruta de esta web (relativa o con su dominio)
    // o un enlace de la app (gymtrack://..., exp://...). Nunca otro sitio web:
    // la página del simulador no debe servir para mandar a alguien a una página
    // ajena que se haga pasar por la tienda.
    private String validarRegreso(String url, String campo, String hostPropio) {
        String valor = url == null ? "" : url.trim();
        if (valor.isEmpty() || valor.length() > 2000) {
            throw new TiendaException(HttpStatus.BAD_REQUEST, "Falta " + campo + ": la dirección a la que vuelve el comprador.");
        }
        URI uri;
        try {
            uri = URI.create(valor);
        } catch (IllegalArgumentException e) {
            throw new TiendaException(HttpStatus.BAD_REQUEST, campo + " no es una dirección válida.");
        }
        String esquema = uri.getScheme() == null ? null : uri.getScheme().toLowerCase(Locale.ROOT);
        if (esquema == null) {
            if (valor.startsWith("//")) throw new TiendaException(HttpStatus.BAD_REQUEST, campo + " no es una dirección válida.");
            return valor;
        }
        if (ESQUEMAS_PROHIBIDOS.contains(esquema)) {
            throw new TiendaException(HttpStatus.BAD_REQUEST, campo + " no es una dirección válida.");
        }
        if (esquema.equals("http") || esquema.equals("https")) {
            String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
            String hostApp = URI.create(urlPublica).getHost();
            if (!host.equals(hostPropio == null ? "" : hostPropio.toLowerCase(Locale.ROOT)) && !host.equalsIgnoreCase(hostApp)) {
                throw new TiendaException(HttpStatus.BAD_REQUEST, campo + " debe ser una página de GymTrack o un enlace de la app.");
            }
        }
        return valor;
    }

    private static String conParametros(String url, Map<String, String> params) {
        String fragmento = "";
        int almohadilla = url.indexOf('#');
        if (almohadilla >= 0) {
            fragmento = url.substring(almohadilla);
            url = url.substring(0, almohadilla);
        }
        StringBuilder sb = new StringBuilder(url);
        char separador = url.contains("?") ? '&' : '?';
        for (Map.Entry<String, String> e : params.entrySet()) {
            sb.append(separador).append(e.getKey()).append('=').append(URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8));
            separador = '&';
        }
        return sb + fragmento;
    }

    private static String alAzar(int largo) {
        StringBuilder sb = new StringBuilder(largo);
        for (int i = 0; i < largo; i++) sb.append(LETRAS.charAt(AZAR.nextInt(LETRAS.length())));
        return sb.toString();
    }
}
