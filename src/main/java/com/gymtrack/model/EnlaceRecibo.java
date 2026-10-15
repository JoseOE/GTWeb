package com.gymtrack.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

// Enlace temporal para compartir el comprobante de un pedido (por WhatsApp,
// por ejemplo): quien lo abre descarga el PDF sin iniciar sesión.
//
// Como en Sesion, el token nunca se guarda: el id es su huella SHA-256, así
// que quien lea la base de datos no puede abrir los comprobantes. Mongo lo
// borra solo al vencer.
@Document(collection = "enlaces_recibo")
public class EnlaceRecibo {

    @Id
    private String id;
    private String orderId;
    // Quién lo generó (el comprador o el dueño del gimnasio).
    private String creadoPor;
    private Instant creadoEn;
    @Indexed(expireAfter = "0s")
    private Instant venceEn;

    public EnlaceRecibo() {}

    public EnlaceRecibo(String id, String orderId, String creadoPor, Instant creadoEn, Instant venceEn) {
        this.id = id;
        this.orderId = orderId;
        this.creadoPor = creadoPor;
        this.creadoEn = creadoEn;
        this.venceEn = venceEn;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getOrderId() { return orderId; }
    public void setOrderId(String orderId) { this.orderId = orderId; }

    public String getCreadoPor() { return creadoPor; }
    public void setCreadoPor(String creadoPor) { this.creadoPor = creadoPor; }

    public Instant getCreadoEn() { return creadoEn; }
    public void setCreadoEn(Instant creadoEn) { this.creadoEn = creadoEn; }

    public Instant getVenceEn() { return venceEn; }
    public void setVenceEn(Instant venceEn) { this.venceEn = venceEn; }
}
