package com.gymtrack.service;

import com.gymtrack.util.MetodosPago;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// Los simuladores de pago (antes medusa/src/modules/sim-*): ninguno habla con
// una pasarela real. Reciben los datos que ya validó Spring (el token de la
// tarjeta canjeado por SimuladorStripeService, la orden aprobada canjeada por
// SimuladorPaypalService) y deciden con ellos:
//  - Tarjeta (sim-stripe): "aprobada" cobra en el acto (pi_sim_… y cargo
//    ch_sim_…); las tarjetas de rechazo responden 402 con su mensaje.
//  - Paynet (sim-paynet): referencia de 18 dígitos (convenio 93 + Luhn) con
//    72 h para pagar; queda autorizado SIN cobrar hasta "Simular pago en tienda".
//  - PayPal (sim-paypal): la orden aprobada se cobra en el acto (captura
//    CAPTURE-SIM_…); la cuenta sin saldo responde 402.
//  - Efectivo del mostrador: cobrado en el acto, sin datos propios.
//
// Lo que devuelve es lo que guarda el pedido en datosPago (sin el token: el
// número completo de la tarjeta y el CVC nunca llegan aquí).
@Service
public class SimuladorPagoService {

    public static final int VIGENCIA_PAYNET_HORAS = 72;

    private static final Map<String, String> RECHAZOS_TARJETA = Map.of(
            "rechazada", "Tu tarjeta fue rechazada. Intenta con otra tarjeta.",
            "fondos_insuficientes", "La tarjeta no tiene fondos suficientes.",
            "vencida", "La tarjeta está vencida.",
            "cvc_incorrecto", "El código de seguridad (CVC) es incorrecto.");
    private static final String RECHAZO_PAYPAL = "PayPal rechazó el pago con esa cuenta. Intenta con otra cuenta u otro método.";
    private static final SecureRandom AZAR = new SecureRandom();

    // capturado = cobrado en el acto; si no, queda pendiente de pago (Paynet).
    public record Resultado(boolean capturado, Map<String, Object> datos) {}

    public Resultado autorizar(String proveedor, Map<String, Object> datos, double monto) {
        if (MetodosPago.EFECTIVO.equals(proveedor)) return new Resultado(true, new LinkedHashMap<>());
        Map<String, Object> d = new LinkedHashMap<>(datos == null ? Map.of() : datos);
        d.remove("token");
        d.remove("session_id");
        String ahora = ahora();
        switch (proveedor) {
            case MetodosPago.STRIPE -> {
                if (datos == null || datos.get("token") == null || datos.get("resultado") == null) {
                    throw new TiendaException(HttpStatus.PAYMENT_REQUIRED, "Falta la tarjeta. Vuelve a capturar sus datos.");
                }
                Object resultado = datos.get("resultado");
                if (!SimuladorStripeService.APROBADA.equals(resultado)) {
                    throw new TiendaException(HttpStatus.PAYMENT_REQUIRED,
                            RECHAZOS_TARJETA.getOrDefault(String.valueOf(resultado), RECHAZOS_TARJETA.get("rechazada")));
                }
                base(d, "pi_sim", "sim-stripe", monto);
                // Como Stripe: el PaymentIntent (pi_sim_...) genera un cargo (ch_sim_...).
                d.put("cargo", nuevoId("ch_sim"));
                cobrado(d, ahora);
                return new Resultado(true, d);
            }
            case MetodosPago.PAYPAL -> {
                if (datos == null || datos.get("orden") == null || datos.get("resultado") == null) {
                    throw new TiendaException(HttpStatus.PAYMENT_REQUIRED, "Falta aprobar el pago en PayPal. Vuelve a intentarlo.");
                }
                if (!SimuladorPaypalService.APROBADA.equals(datos.get("resultado"))) {
                    throw new TiendaException(HttpStatus.PAYMENT_REQUIRED, RECHAZO_PAYPAL);
                }
                base(d, "PAYID-SIM", "sim-paypal", monto);
                // Como PayPal: la orden aprobada se captura y deja un id de captura.
                d.put("captura", nuevoId("CAPTURE-SIM"));
                cobrado(d, ahora);
                return new Resultado(true, d);
            }
            case MetodosPago.PAYNET -> {
                base(d, "paynet_sim", "sim-paynet", monto);
                d.put("referencia", referenciaPaynet());
                d.put("vence", Instant.now().plus(Duration.ofHours(VIGENCIA_PAYNET_HORAS)).truncatedTo(ChronoUnit.MILLIS).toString());
                d.put("estado", "autorizado");
                d.put("autorizadoEn", ahora);
                return new Resultado(false, d);
            }
            default -> throw new TiendaException(HttpStatus.BAD_REQUEST, "Elige cómo quieres pagar.");
        }
    }

    // La ficha Paynet se pagó en la tienda.
    public static void capturar(Map<String, Object> datos) {
        datos.put("estado", "capturado");
        datos.putIfAbsent("capturadoEn", ahora());
    }

    public static void cancelar(Map<String, Object> datos) {
        if (datos.isEmpty()) return;
        datos.put("estado", "cancelado");
        datos.put("canceladoEn", ahora());
    }

    // Reembolso simulado de todo lo cobrado (no se mueve dinero real).
    @SuppressWarnings("unchecked")
    public static void reembolsar(Map<String, Object> datos, double monto) {
        List<Object> reembolsos = datos.get("reembolsos") instanceof List<?> l ? new ArrayList<>((List<Object>) l) : new ArrayList<>();
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("id", nuevoId("re_sim"));
        r.put("monto", monto);
        r.put("fecha", ahora());
        reembolsos.add(r);
        datos.put("reembolsos", reembolsos);
        datos.put("estado", "reembolsado");
    }

    // "93" identifica al convenio simulado de GymTrack; luego 15 dígitos al azar
    // y el dígito verificador de Luhn (permite detectar un número mal capturado).
    static String referenciaPaynet() {
        StringBuilder base = new StringBuilder("93");
        for (int i = 0; i < 15; i++) base.append(AZAR.nextInt(10));
        return base.toString() + digitoVerificador(base.toString());
    }

    static int digitoVerificador(String digitos) {
        int suma = 0;
        boolean doblar = true;
        for (int i = digitos.length() - 1; i >= 0; i--) {
            int d = digitos.charAt(i) - '0';
            if (doblar) {
                d *= 2;
                if (d > 9) d -= 9;
            }
            suma += d;
            doblar = !doblar;
        }
        return (10 - (suma % 10)) % 10;
    }

    private static void base(Map<String, Object> d, String prefijo, String simulador, double monto) {
        d.put("id", nuevoId(prefijo));
        d.put("simulador", simulador);
        d.put("monto", monto);
        d.put("moneda", TiendaGymService.MONEDA);
    }

    private static void cobrado(Map<String, Object> d, String ahora) {
        d.put("estado", "capturado");
        d.put("autorizadoEn", ahora);
        d.put("capturadoEn", ahora);
    }

    // Mismo formato que los ids de los simuladores de Medusa: prefijo_ + 18 hex.
    static String nuevoId(String prefijo) {
        byte[] bytes = new byte[9];
        AZAR.nextBytes(bytes);
        return prefijo + "_" + HexFormat.of().formatHex(bytes);
    }

    private static String ahora() {
        return Instant.now().truncatedTo(ChronoUnit.MILLIS).toString();
    }
}
