package com.gymtrack.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

// Código o enlace de un solo uso que llega por correo (verificar la cuenta,
// recuperar la contraseña, confirmar un correo nuevo). Solo se guarda el hash:
// quien lea la base de datos no puede usar los códigos.
@Document(collection = "tokens_cuenta")
public class TokenCuenta {

    public static final String VERIFICAR_CORREO = "verificar_correo";
    public static final String RECUPERAR_CONTRASENA = "recuperar_contrasena";
    public static final String CAMBIAR_CORREO = "cambiar_correo";

    @Id
    private String id;
    @Indexed
    private String userId;
    private String tipo;
    // Código de 6 dígitos que el usuario escribe a mano.
    private String codigoHash;
    // Token largo que va en el botón/enlace del correo.
    @Indexed
    private String enlaceHash;
    // Dato extra del trámite; en un cambio de correo, el correo nuevo.
    private String dato;
    private int intentos;
    private Instant creado;
    // MongoDB borra el documento en cuanto pasa esta fecha (índice TTL).
    @Indexed(expireAfterSeconds = 0)
    private Instant expira;

    public boolean vigente() {
        return expira != null && expira.isAfter(Instant.now());
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getTipo() { return tipo; }
    public void setTipo(String tipo) { this.tipo = tipo; }

    public String getCodigoHash() { return codigoHash; }
    public void setCodigoHash(String codigoHash) { this.codigoHash = codigoHash; }

    public String getEnlaceHash() { return enlaceHash; }
    public void setEnlaceHash(String enlaceHash) { this.enlaceHash = enlaceHash; }

    public String getDato() { return dato; }
    public void setDato(String dato) { this.dato = dato; }

    public int getIntentos() { return intentos; }
    public void setIntentos(int intentos) { this.intentos = intentos; }

    public Instant getCreado() { return creado; }
    public void setCreado(Instant creado) { this.creado = creado; }

    public Instant getExpira() { return expira; }
    public void setExpira(Instant expira) { this.expira = expira; }
}
