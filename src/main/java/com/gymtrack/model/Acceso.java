package com.gymtrack.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

// Bitácora de la puerta: cada entrada, salida o acceso negado.
@Document(collection = "accesos")
public class Acceso {

    public static final String ENTRADA = "ENTRADA";
    public static final String SALIDA = "SALIDA";
    public static final String NEGADO = "NEGADO";

    @Id
    private String id;
    @Indexed
    private String gymId;
    private String lectorId;
    private String epc;
    @Indexed
    private String userId;      // null si el tag no es de nadie
    private String nombre;
    private String tipo;
    private String motivo;      // por qué se negó, o quién abrió desde el panel
    private String solicitudId;
    private Instant fecha;

    public Acceso() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getGymId() { return gymId; }
    public void setGymId(String gymId) { this.gymId = gymId; }

    public String getLectorId() { return lectorId; }
    public void setLectorId(String lectorId) { this.lectorId = lectorId; }

    public String getEpc() { return epc; }
    public void setEpc(String epc) { this.epc = epc; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }

    public String getTipo() { return tipo; }
    public void setTipo(String tipo) { this.tipo = tipo; }

    public String getMotivo() { return motivo; }
    public void setMotivo(String motivo) { this.motivo = motivo; }

    public String getSolicitudId() { return solicitudId; }
    public void setSolicitudId(String solicitudId) { this.solicitudId = solicitudId; }

    public Instant getFecha() { return fecha; }
    public void setFecha(Instant fecha) { this.fecha = fecha; }
}
