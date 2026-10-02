package com.gymtrack.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

// Imagen de un producto de la tienda. Se guarda en Mongo y no en disco porque
// el disco de Render se borra en cada despliegue; Medusa solo guarda la URL
// pública (/api/imagenes/{id}). El panel la redimensiona antes de subirla,
// igual que el logo del gimnasio, así que cada una pesa unos cuantos KB.
@Document(collection = "imagenes")
public class Imagen {

    @Id
    private String id;
    private String gymId;
    private String tipoContenido;
    private byte[] datos;
    private Instant creadaEn;

    public Imagen() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getGymId() { return gymId; }
    public void setGymId(String gymId) { this.gymId = gymId; }

    public String getTipoContenido() { return tipoContenido; }
    public void setTipoContenido(String tipoContenido) { this.tipoContenido = tipoContenido; }

    public byte[] getDatos() { return datos; }
    public void setDatos(byte[] datos) { this.datos = datos; }

    public Instant getCreadaEn() { return creadaEn; }
    public void setCreadaEn(Instant creadaEn) { this.creadaEn = creadaEn; }
}
