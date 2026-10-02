package com.gymtrack.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

// Token de una tarjeta de prueba, como los de Stripe.js. El número completo y
// el CVC nunca se guardan: solo la marca, los últimos 4 y qué hará el
// simulador al cobrar (aprobar, rechazar...). Es de un solo uso, de quien lo
// pidió, y Mongo lo borra solo a los 30 minutos.
@Document(collection = "tokens_tarjeta")
public class TokenTarjeta {

    @Id
    private String token;
    private String userId;
    private String marca;
    private String ultimos4;
    // "MM/AA"
    private String vencimiento;
    private String titular;
    // aprobada | rechazada | fondos_insuficientes | vencida | cvc_incorrecto
    private String resultado;
    @Indexed(expireAfter = "30m")
    private Instant creadoEn;
    private Instant usadoEn;

    public TokenTarjeta() {}

    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getMarca() { return marca; }
    public void setMarca(String marca) { this.marca = marca; }

    public String getUltimos4() { return ultimos4; }
    public void setUltimos4(String ultimos4) { this.ultimos4 = ultimos4; }

    public String getVencimiento() { return vencimiento; }
    public void setVencimiento(String vencimiento) { this.vencimiento = vencimiento; }

    public String getTitular() { return titular; }
    public void setTitular(String titular) { this.titular = titular; }

    public String getResultado() { return resultado; }
    public void setResultado(String resultado) { this.resultado = resultado; }

    public Instant getCreadoEn() { return creadoEn; }
    public void setCreadoEn(Instant creadoEn) { this.creadoEn = creadoEn; }

    public Instant getUsadoEn() { return usadoEn; }
    public void setUsadoEn(Instant usadoEn) { this.usadoEn = usadoEn; }
}
