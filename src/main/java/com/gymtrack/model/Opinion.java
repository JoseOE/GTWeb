package com.gymtrack.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

// Opinión de un usuario sobre GymTrack: una por cuenta (la cambia cuando
// quiera). Las de 4 estrellas o más salen en el carrusel de la página de
// inicio con la foto de su gimnasio (ver OpinionService).
@Document(collection = "opiniones")
public class Opinion {

    @Id
    private String id;
    @Indexed(unique = true)
    private String userId;
    @Indexed
    private String gymId;
    // "owner" o "member", al momento de opinar.
    private String rol;
    private int estrellas;
    private String texto;
    @Indexed
    private Instant creadaEn;
    private Instant actualizadaEn;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getGymId() { return gymId; }
    public void setGymId(String gymId) { this.gymId = gymId; }

    public String getRol() { return rol; }
    public void setRol(String rol) { this.rol = rol; }

    public int getEstrellas() { return estrellas; }
    public void setEstrellas(int estrellas) { this.estrellas = estrellas; }

    public String getTexto() { return texto; }
    public void setTexto(String texto) { this.texto = texto; }

    public Instant getCreadaEn() { return creadaEn; }
    public void setCreadaEn(Instant creadaEn) { this.creadaEn = creadaEn; }

    public Instant getActualizadaEn() { return actualizadaEn; }
    public void setActualizadaEn(Instant actualizadaEn) { this.actualizadaEn = actualizadaEn; }
}
