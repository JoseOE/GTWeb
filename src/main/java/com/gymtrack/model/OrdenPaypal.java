package com.gymtrack.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

// Orden de PayPal simulada, como las que crea la API de PayPal antes de que el
// comprador apruebe el pago. Su id (PAYID-SIM-...) viaja en la URL de
// paypal-sim.html, así que hace de llave: quien lo tiene puede aprobarla o
// cancelarla, pero solo su dueño puede pagar con ella. No guarda contraseñas:
// la cuenta es un correo de prueba. Mongo la borra sola a las 3 horas.
@Document(collection = "ordenes_paypal")
public class OrdenPaypal {

    public static final String CREADA = "creada";
    public static final String APROBADA = "aprobada";
    public static final String CANCELADA = "cancelada";
    public static final String USADA = "usada";

    @Id
    private String id;
    private String userId;
    private String gymId;
    // web o app
    private String canal;
    // Nombre del gimnasio, para que el simulador diga a quién se le paga.
    private String comercio;
    private Double monto;
    private String moneda;
    private Integer articulos;
    // creada | aprobada | cancelada | usada
    private String estado;
    // Correo de la cuenta de prueba con la que se aprobó.
    private String cuenta;
    private String payerId;
    // Qué pasará al cobrar: aprobada, o rechazada con la cuenta sin saldo.
    private String resultado;
    // A dónde vuelve el comprador: una página de esta web o un enlace de la app.
    private String returnUrl;
    private String cancelUrl;
    @Indexed(expireAfter = "3h")
    private Instant creadaEn;
    private Instant aprobadaEn;
    private Instant usadaEn;

    public OrdenPaypal() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getGymId() { return gymId; }
    public void setGymId(String gymId) { this.gymId = gymId; }

    public String getCanal() { return canal; }
    public void setCanal(String canal) { this.canal = canal; }

    public String getComercio() { return comercio; }
    public void setComercio(String comercio) { this.comercio = comercio; }

    public Double getMonto() { return monto; }
    public void setMonto(Double monto) { this.monto = monto; }

    public String getMoneda() { return moneda; }
    public void setMoneda(String moneda) { this.moneda = moneda; }

    public Integer getArticulos() { return articulos; }
    public void setArticulos(Integer articulos) { this.articulos = articulos; }

    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }

    public String getCuenta() { return cuenta; }
    public void setCuenta(String cuenta) { this.cuenta = cuenta; }

    public String getPayerId() { return payerId; }
    public void setPayerId(String payerId) { this.payerId = payerId; }

    public String getResultado() { return resultado; }
    public void setResultado(String resultado) { this.resultado = resultado; }

    public String getReturnUrl() { return returnUrl; }
    public void setReturnUrl(String returnUrl) { this.returnUrl = returnUrl; }

    public String getCancelUrl() { return cancelUrl; }
    public void setCancelUrl(String cancelUrl) { this.cancelUrl = cancelUrl; }

    public Instant getCreadaEn() { return creadaEn; }
    public void setCreadaEn(Instant creadaEn) { this.creadaEn = creadaEn; }

    public Instant getAprobadaEn() { return aprobadaEn; }
    public void setAprobadaEn(Instant aprobadaEn) { this.aprobadaEn = aprobadaEn; }

    public Instant getUsadaEn() { return usadaEn; }
    public void setUsadaEn(Instant usadaEn) { this.usadaEn = usadaEn; }
}
