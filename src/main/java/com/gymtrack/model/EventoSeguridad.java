package com.gymtrack.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

// Un renglón de la bitácora de seguridad (ISO 27001, A.12.4): quién hizo qué,
// cuándo y desde dónde. Lo escribe BitacoraService; nadie lo edita. Se borra
// solo a los RETENCION_DIAS (índice TTL), como dice el Aviso de privacidad.
@Document(collection = "bitacora")
@CompoundIndexes({
        @CompoundIndex(name = "gym_fecha", def = "{'gymId': 1, 'fecha': -1}"),
        @CompoundIndex(name = "usuario_fecha", def = "{'userId': 1, 'fecha': -1}")
})
public class EventoSeguridad {

    public static final int RETENCION_DIAS = 180;

    @Id
    private String id;
    // LOGIN_EXITOSO, LOGIN_FALLIDO, ACCESO_DENEGADO, PEDIDO_REEMBOLSADO… (ver BitacoraService).
    private String tipo;
    private String descripcion;
    // Quién (null si no hay sesión) y el correo con el que se intentó, en los inicios de sesión.
    private String userId;
    private String email;
    // El gimnasio afectado, si la acción fue sobre uno.
    private String gymId;
    private String metodo;
    private String ruta;
    private Integer estado;
    private String ip;
    private String agente;
    private Instant fecha;
    @Indexed(expireAfterSeconds = 0)
    private Instant expira;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getTipo() { return tipo; }
    public void setTipo(String tipo) { this.tipo = tipo; }

    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getGymId() { return gymId; }
    public void setGymId(String gymId) { this.gymId = gymId; }

    public String getMetodo() { return metodo; }
    public void setMetodo(String metodo) { this.metodo = metodo; }

    public String getRuta() { return ruta; }
    public void setRuta(String ruta) { this.ruta = ruta; }

    public Integer getEstado() { return estado; }
    public void setEstado(Integer estado) { this.estado = estado; }

    public String getIp() { return ip; }
    public void setIp(String ip) { this.ip = ip; }

    public String getAgente() { return agente; }
    public void setAgente(String agente) { this.agente = agente; }

    public Instant getFecha() { return fecha; }
    public void setFecha(Instant fecha) { this.fecha = fecha; }

    public Instant getExpira() { return expira; }
    public void setExpira(Instant expira) { this.expira = expira; }
}
