package com.gymtrack.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

// Foto que tomó la XIAO en un acceso negado. A diferencia de Imagen, no tiene
// URL pública: solo el dueño del gimnasio la ve desde el panel, y Mongo la
// borra sola a los 30 días.
@Document(collection = "fotos_acceso")
public class FotoAcceso {

    @Id
    private String id;
    private String gymId;
    private String lectorId;
    private String solicitudId;
    private byte[] datos;
    @Indexed(expireAfterSeconds = 30 * 24 * 3600)
    private Instant creadaEn;

    public FotoAcceso() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getGymId() { return gymId; }
    public void setGymId(String gymId) { this.gymId = gymId; }

    public String getLectorId() { return lectorId; }
    public void setLectorId(String lectorId) { this.lectorId = lectorId; }

    public String getSolicitudId() { return solicitudId; }
    public void setSolicitudId(String solicitudId) { this.solicitudId = solicitudId; }

    public byte[] getDatos() { return datos; }
    public void setDatos(byte[] datos) { this.datos = datos; }

    public Instant getCreadaEn() { return creadaEn; }
    public void setCreadaEn(Instant creadaEn) { this.creadaEn = creadaEn; }
}
