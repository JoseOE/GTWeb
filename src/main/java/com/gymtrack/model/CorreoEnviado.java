package com.gymtrack.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

// Correo automático ya enviado. El id es "tipo:referencia" (p. ej.
// "compra:order_..."): insertarlo es lo que "aparta" el envío, así que aunque
// dos peticiones confirmen el mismo pedido a la vez, el correo sale una sola vez.
@Document(collection = "correos_enviados")
public class CorreoEnviado {

    @Id
    private String id;
    private Instant enviadoEn;

    public CorreoEnviado() {}

    public CorreoEnviado(String id) {
        this.id = id;
        this.enviadoEn = Instant.now();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public Instant getEnviadoEn() { return enviadoEn; }
    public void setEnviadoEn(Instant enviadoEn) { this.enviadoEn = enviadoEn; }
}
