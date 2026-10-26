package com.gymtrack.service;

import com.gymtrack.model.Gym;
import com.gymtrack.model.OrdenPaypal;
import com.gymtrack.model.User;
import com.gymtrack.model.VinculoPaypal;
import com.gymtrack.repository.GymRepository;
import com.gymtrack.repository.OrdenPaypalRepository;
import com.gymtrack.repository.VinculoPaypalRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

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
//
// Cuenta vinculada: si al crear la orden se pide "recordar", al pagar con ella
// la cuenta queda vinculada al usuario (como un acuerdo de pago de PayPal). Las
// siguientes compras mandan {"vinculo": true} y se cobran sin abrir el
// simulador. No se guarda contraseña ni tarjeta: solo la cuenta y el acuerdo.
@Service
public class SimuladorPaypalService {

    public static final String APROBADA = "aprobada";
    public static final String RECHAZADA = "rechazada";
    private static final Duration VIGENCIA = Duration.ofHours(3);
    private static final String LETRAS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final SecureRandom AZAR = new SecureRandom();

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
    private final VinculoPaypalRepository vinculos;
    private final String urlPublica;
    private final Regresos regresos;

    public SimuladorPaypalService(OrdenPaypalRepository ordenes, GymRepository gymRepository, VinculoPaypalRepository vinculos,
                                  @Value("${app.url-publica:http://localhost:8080}") String urlPublica, Regresos regresos) {
        this.ordenes = ordenes;
        this.gymRepository = gymRepository;
        this.vinculos = vinculos;
        this.urlPublica = urlPublica.replaceAll("/+$", "");
        this.regresos = regresos;
    }

    // Crea la orden por el total del carrito. hostPropio es el dominio por el
    // que llegó la petición: junto con APP_URL, son los únicos sitios web a los
    // que se puede volver.
    public Map<String, Object> crear(User comprador, String canal, double total, int articulos,
                                     String returnUrl, String cancelUrl, String hostPropio, boolean recordar) {
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
        orden.setRecordar(recordar);
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
        v.put("recordar", orden.isRecordar());
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
        if (datos != null && Boolean.TRUE.equals(datos.get("vinculo"))) return conVinculo(userId);
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
        // Titular de la cuenta, como lo muestra el recibo de PayPal (solo las cuentas de la lista).
        CUENTAS.stream().filter(c -> c.correo().equals(orden.getCuenta()) && APROBADA.equals(c.resultado()))
                .findFirst().ifPresent(c -> seguros.put("nombre", c.nombre()));
        seguros.put("resultado", orden.getResultado());
        // Solo se vincula una cuenta con la que PayPal sí cobra.
        if (orden.isRecordar() && APROBADA.equals(orden.getResultado())) {
            VinculoPaypal v = vinculos.findById(userId).orElseGet(VinculoPaypal::new);
            v.setUserId(userId);
            v.setCuenta(orden.getCuenta());
            v.setNombre((String) seguros.get("nombre"));
            v.setPayerId(orden.getPayerId());
            v.setAcuerdo("B-SIM-" + alAzar(17));
            v.setCreadoEn(Instant.now());
            v.setUltimoUsoEn(Instant.now());
            vinculos.save(v);
            seguros.put("acuerdo", v.getAcuerdo());
            seguros.put("vinculada", true);
        }
        return seguros;
    }

    // Cobra con la cuenta vinculada: no hay ventana de PayPal ni orden que
    // aprobar. La cuenta sin saldo sigue rechazándose al cobrar.
    private Map<String, Object> conVinculo(String userId) {
        VinculoPaypal v = vinculos.findById(userId).orElseThrow(() ->
                new TiendaException(HttpStatus.BAD_REQUEST, "Tu cuenta PayPal ya no está vinculada. Elige tu cuenta de nuevo."));
        v.setUltimoUsoEn(Instant.now());
        vinculos.save(v);
        Map<String, Object> seguros = new LinkedHashMap<>();
        seguros.put("orden", "PAYID-SIM-" + alAzar(16));
        seguros.put("payerId", v.getPayerId());
        seguros.put("cuenta", v.getCuenta());
        if (v.getNombre() != null) seguros.put("nombre", v.getNombre());
        seguros.put("resultado", CUENTAS.stream().filter(c -> c.correo().equals(v.getCuenta()))
                .map(CuentaDePrueba::resultado).findFirst().orElse(APROBADA));
        seguros.put("acuerdo", v.getAcuerdo());
        seguros.put("vinculada", true);
        return seguros;
    }

    // Lo que muestran el checkout y "Mi cuenta".
    public Map<String, Object> vinculo(String userId) {
        Map<String, Object> r = new LinkedHashMap<>();
        VinculoPaypal v = vinculos.findById(userId).orElse(null);
        r.put("vinculada", v != null);
        if (v != null) {
            r.put("cuenta", v.getCuenta());
            r.put("nombre", v.getNombre());
            r.put("desde", v.getCreadoEn());
        }
        return r;
    }

    public Map<String, Object> desvincular(String userId) {
        vinculos.deleteById(userId);
        return vinculo(userId);
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

    private String validarRegreso(String url, String campo, String hostPropio) {
        return regresos.validar(url, campo, hostPropio);
    }

    private static String conParametros(String url, Map<String, String> params) {
        return Regresos.conParametros(url, params);
    }

    private static String alAzar(int largo) {
        StringBuilder sb = new StringBuilder(largo);
        for (int i = 0; i < largo; i++) sb.append(LETRAS.charAt(AZAR.nextInt(LETRAS.length())));
        return sb.toString();
    }
}
