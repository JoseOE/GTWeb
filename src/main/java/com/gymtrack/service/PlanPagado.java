package com.gymtrack.service;

import java.util.Set;

// Lo que BillingService necesita saber de un plan para extender una membresía.
// unidad: "dia" | "semana" | "mes"; cantidad: cuántas de esas unidades cubre.
public record PlanPagado(String planId, String nombre, String unidad, int cantidad) {

    public static final Set<String> UNIDADES = Set.of("dia", "semana", "mes");

    // Set.of no acepta null en contains(): una inscripción no tiene unidad.
    public static boolean esUnidad(String unidad) {
        return unidad != null && UNIDADES.contains(unidad);
    }

    // Lo que valía un pago antes de que existieran los planes: un mes.
    public static final PlanPagado MENSUAL = new PlanPagado(null, null, "mes", 1);

    public boolean esPorMes() {
        return "mes".equals(unidad);
    }
}
