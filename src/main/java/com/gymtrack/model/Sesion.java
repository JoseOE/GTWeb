package com.gymtrack.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

// Una sesión abierta: la entrega el login (y la verificación del correo) y la
// manda la página en cada llamada (Authorization: Bearer <token>).
//
// El token nunca se guarda: el id es su huella SHA-256, así que quien lea la
// base de datos no puede usar las sesiones. Mongo la borra sola al vencer.
@Document(collection = "sesiones")
public class Sesion {

    @Id
    private String id;
    @Indexed
    private String userId;
    private Instant creadaEn;
    @Indexed(expireAfter = "0s")
    private Instant expiraEn;

    public Sesion() {}

    public Sesion(String id, String userId, Instant creadaEn, Instant expiraEn) {
        this.id = id;
        this.userId = userId;
        this.creadaEn = creadaEn;
        this.expiraEn = expiraEn;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public Instant getCreadaEn() { return creadaEn; }
    public void setCreadaEn(Instant creadaEn) { this.creadaEn = creadaEn; }

    public Instant getExpiraEn() { return expiraEn; }
    public void setExpiraEn(Instant expiraEn) { this.expiraEn = expiraEn; }
}
