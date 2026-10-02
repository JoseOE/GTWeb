package com.gymtrack.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

// Aviso de Medusa ya procesado. El id es "evento:pedido" (o "evento:pago"), así
// que si Medusa repite el mismo aviso —por un reintento o porque el evento se
// emitió dos veces— el segundo se reconoce y no se vuelve a aplicar.
// Mongo los borra solos a los 30 días: para entonces ya no llegan repetidos.
@Document(collection = "avisos_tienda")
public class AvisoTienda {

    @Id
    private String id;
    private String evento;
    private String orderId;
    @Indexed(expireAfter = "30d")
    private Instant procesadoEn;

    public AvisoTienda() {}

    public AvisoTienda(String id, String evento, String orderId) {
        this.id = id;
        this.evento = evento;
        this.orderId = orderId;
        this.procesadoEn = Instant.now();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getEvento() { return evento; }
    public void setEvento(String evento) { this.evento = evento; }

    public String getOrderId() { return orderId; }
    public void setOrderId(String orderId) { this.orderId = orderId; }

    public Instant getProcesadoEn() { return procesadoEn; }
    public void setProcesadoEn(Instant procesadoEn) { this.procesadoEn = procesadoEn; }
}
