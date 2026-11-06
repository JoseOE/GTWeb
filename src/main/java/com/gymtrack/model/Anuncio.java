package com.gymtrack.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.time.LocalDate;

// Anuncio que el dueño publica para sus miembros ("Cerramos el lunes",
// "2×1 en proteína"). Se ve en Inicio de la app mientras está vigente.
@Document(collection = "anuncios")
public class Anuncio {

    public static final String AVISO = "aviso";
    public static final String PROMOCION = "promocion";
    public static final String CIERRE = "cierre";

    @Id
    private String id;
    @Indexed
    private String gymId;
    private String titulo;
    private String texto;
    // aviso, promocion o cierre: cambia el ícono y el color en la app.
    private String tipo;
    // Se ve de "desde" a "hasta", inclusive (hora de México). Sin "hasta", no vence.
    private LocalDate desde;
    private LocalDate hasta;
    // El dueño lo puede pausar sin borrarlo.
    private boolean pausado;
    // Cuándo se mandó la notificación a los miembros (solo una vez por anuncio).
    private Instant avisadoEn;
    private Instant creadoEn;
    private Instant actualizadoEn;

    // vigente, programado (empieza después), vencido o pausado.
    public String estado(LocalDate hoy) {
        if (pausado) return "pausado";
        if (desde != null && hoy.isBefore(desde)) return "programado";
        if (hasta != null && hoy.isAfter(hasta)) return "vencido";
        return "vigente";
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getGymId() { return gymId; }
    public void setGymId(String gymId) { this.gymId = gymId; }

    public String getTitulo() { return titulo; }
    public void setTitulo(String titulo) { this.titulo = titulo; }

    public String getTexto() { return texto; }
    public void setTexto(String texto) { this.texto = texto; }

    public String getTipo() { return tipo; }
    public void setTipo(String tipo) { this.tipo = tipo; }

    public LocalDate getDesde() { return desde; }
    public void setDesde(LocalDate desde) { this.desde = desde; }

    public LocalDate getHasta() { return hasta; }
    public void setHasta(LocalDate hasta) { this.hasta = hasta; }

    public boolean isPausado() { return pausado; }
    public void setPausado(boolean pausado) { this.pausado = pausado; }

    public Instant getAvisadoEn() { return avisadoEn; }
    public void setAvisadoEn(Instant avisadoEn) { this.avisadoEn = avisadoEn; }

    public Instant getCreadoEn() { return creadoEn; }
    public void setCreadoEn(Instant creadoEn) { this.creadoEn = creadoEn; }

    public Instant getActualizadoEn() { return actualizadoEn; }
    public void setActualizadoEn(Instant actualizadoEn) { this.actualizadoEn = actualizadoEn; }
}
