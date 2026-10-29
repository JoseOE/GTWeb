package com.gymtrack.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

// Un acceso negado que el dueño puede revisar desde el panel: ve las fotos
// de la XIAO y decide si abre la puerta. Cuando decide, el lector se entera
// en su siguiente consulta de comandos (comandoEntregado pasa a true).
@Document(collection = "solicitudes_acceso")
public class SolicitudAcceso {

    public static final String PENDIENTE = "PENDIENTE";
    public static final String APROBADA = "APROBADA";
    public static final String RECHAZADA = "RECHAZADA";

    @Id
    private String id;
    @Indexed
    private String gymId;
    @Indexed
    private String lectorId;
    private String epc;
    private String userId;      // si el tag es de un socio (p. ej. con la membresía vencida)
    private String nombre;
    private String motivo;
    private String estado = PENDIENTE;
    private Instant creadaEn;
    private Instant atendidaEn;
    private String atendidaPor;
    private boolean comandoEntregado;
    private List<String> fotos = new ArrayList<>();

    public SolicitudAcceso() {}

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

    public String getMotivo() { return motivo; }
    public void setMotivo(String motivo) { this.motivo = motivo; }

    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }

    public Instant getCreadaEn() { return creadaEn; }
    public void setCreadaEn(Instant creadaEn) { this.creadaEn = creadaEn; }

    public Instant getAtendidaEn() { return atendidaEn; }
    public void setAtendidaEn(Instant atendidaEn) { this.atendidaEn = atendidaEn; }

    public String getAtendidaPor() { return atendidaPor; }
    public void setAtendidaPor(String atendidaPor) { this.atendidaPor = atendidaPor; }

    public boolean isComandoEntregado() { return comandoEntregado; }
    public void setComandoEntregado(boolean comandoEntregado) { this.comandoEntregado = comandoEntregado; }

    public List<String> getFotos() { return fotos; }
    public void setFotos(List<String> fotos) { this.fotos = fotos; }
}
