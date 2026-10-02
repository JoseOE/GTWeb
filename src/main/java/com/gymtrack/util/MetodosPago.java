package com.gymtrack.util;

import java.util.Map;

// Proveedores de pago registrados en Medusa (medusa/medusa-config.ts) y el
// nombre con el que se muestran en el panel, los recibos y el historial de pagos.
public final class MetodosPago {

    public static final String STRIPE = "pp_sim-stripe_default";
    public static final String PAYNET = "pp_sim-paynet_default";
    public static final String PAYPAL = "pp_sim-paypal_default";
    // Proveedor manual de Medusa: el efectivo del mostrador.
    public static final String EFECTIVO = "pp_system_default";

    private static final Map<String, String> NOMBRES = Map.of(
            STRIPE, "Tarjeta",
            PAYNET, "Paynet",
            PAYPAL, "PayPal",
            EFECTIVO, "Efectivo");

    private MetodosPago() {}

    public static String nombre(String proveedorId) {
        if (proveedorId == null) return "Sin pago";
        return NOMBRES.getOrDefault(proveedorId, proveedorId);
    }
}
