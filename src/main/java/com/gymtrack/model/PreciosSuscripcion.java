package com.gymtrack.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

// Lo que cobra GymTrack a los gimnasios. Un solo documento ("suscripcion")
// que el equipo cambia desde su panel. Precios en pesos, con IVA incluido.
@Document(collection = "configuracion")
public class PreciosSuscripcion {

    public static final String ID = "suscripcion";
    // Los de arranque: el anual equivale a 10 meses (dos de regalo).
    public static final double ANUAL_INICIAL = 4990;
    public static final double MENSUAL_INICIAL = 499;
    public static final int GRACIA_INICIAL = 7;

    @Id
    private String id = ID;
    private double anual = ANUAL_INICIAL;
    private double mensual = MENSUAL_INICIAL;
    // Días después de vencer en los que el panel sigue funcionando (con avisos).
    private int diasGracia = GRACIA_INICIAL;
    private Instant actualizadoEn;

    public PreciosSuscripcion() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public double getAnual() { return anual; }
    public void setAnual(double anual) { this.anual = anual; }

    public double getMensual() { return mensual; }
    public void setMensual(double mensual) { this.mensual = mensual; }

    public int getDiasGracia() { return diasGracia; }
    public void setDiasGracia(int diasGracia) { this.diasGracia = diasGracia; }

    public Instant getActualizadoEn() { return actualizadoEn; }
    public void setActualizadoEn(Instant actualizadoEn) { this.actualizadoEn = actualizadoEn; }
}
