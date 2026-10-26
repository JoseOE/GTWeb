package com.gymtrack.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

// Una Checkout Session de Stripe que abrió GymTrack: de quién es, de qué
// gimnasio y canal, y por cuánto. Al volver de la página de Stripe el cobro se
// revisa contra esto (no contra lo que mande el navegador) y se usa una sola
// vez. Mongo la borra sola un día después.
@Document(collection = "sesiones_stripe")
public class SesionStripe {

    public static final String CREADA = "creada";
    public static final String USADA = "usada";
    public static final String FALLIDA = "fallida";

    // El id de la sesión en Stripe (cs_test_...).
    @Id
    private String id;
    private String userId;
    private String gymId;
    // web, app o mostrador
    private String canal;
    private Double monto;
    // creada | usada | fallida
    private String estado;
    private String paymentIntent;
    @Indexed(expireAfter = "1d")
    private Instant creadaEn;
    private Instant usadaEn;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getGymId() { return gymId; }
    public void setGymId(String gymId) { this.gymId = gymId; }

    public String getCanal() { return canal; }
    public void setCanal(String canal) { this.canal = canal; }

    public Double getMonto() { return monto; }
    public void setMonto(Double monto) { this.monto = monto; }

    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }

    public String getPaymentIntent() { return paymentIntent; }
    public void setPaymentIntent(String paymentIntent) { this.paymentIntent = paymentIntent; }

    public Instant getCreadaEn() { return creadaEn; }
    public void setCreadaEn(Instant creadaEn) { this.creadaEn = creadaEn; }

    public Instant getUsadaEn() { return usadaEn; }
    public void setUsadaEn(Instant usadaEn) { this.usadaEn = usadaEn; }
}
