package com.gymtrack.util;

import java.time.LocalDate;
import java.time.ZoneId;

// El día de hoy en México. Render corre en UTC: con LocalDate.now() a secas,
// después de las 6 de la tarde ya sería "mañana" para los cortes de pago, los
// días que faltan para vencer y la fecha de un pago registrado en recepción.
public final class Fechas {

    public static final ZoneId ZONA_MX = ZoneId.of("America/Mexico_City");

    private Fechas() {}

    public static LocalDate hoy() {
        return LocalDate.now(ZONA_MX);
    }
}
