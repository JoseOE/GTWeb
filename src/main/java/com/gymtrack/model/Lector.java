package com.gymtrack.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

// Un lector RFID de la puerta (LILYGO + YRM1004, con la XIAO como cámara).
// Se identifica con una llave que el panel muestra una sola vez; aquí solo
// queda su SHA-256, como una contraseña.
@Document(collection = "lectores")
public class Lector {

    @Id
    private String id;
    private String gymId;
    private String nombre;
    @Indexed(unique = true)
    private String llaveHash;
    private Instant creadoEn;
    private Instant ultimaConexion;
    // Modo registro: el siguiente tag que lea no abre ni niega, solo se captura
    // para que el dueño lo asigne a un socio desde el panel.
    private Instant registroHasta;
    private String epcCapturado;
    private Instant epcCapturadoEn;

    public Lector() {}

    public Instant getRegistroHasta() { return registroHasta; }
    public void setRegistroHasta(Instant registroHasta) { this.registroHasta = registroHasta; }

    public String getEpcCapturado() { return epcCapturado; }
    public void setEpcCapturado(String epcCapturado) { this.epcCapturado = epcCapturado; }

    public Instant getEpcCapturadoEn() { return epcCapturadoEn; }
    public void setEpcCapturadoEn(Instant epcCapturadoEn) { this.epcCapturadoEn = epcCapturadoEn; }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getGymId() { return gymId; }
    public void setGymId(String gymId) { this.gymId = gymId; }

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }

    public String getLlaveHash() { return llaveHash; }
    public void setLlaveHash(String llaveHash) { this.llaveHash = llaveHash; }

    public Instant getCreadoEn() { return creadoEn; }
    public void setCreadoEn(Instant creadoEn) { this.creadoEn = creadoEn; }

    public Instant getUltimaConexion() { return ultimaConexion; }
    public void setUltimaConexion(Instant ultimaConexion) { this.ultimaConexion = ultimaConexion; }
}
