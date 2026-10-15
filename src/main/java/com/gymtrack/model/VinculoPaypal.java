package com.gymtrack.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

// Cuenta PayPal vinculada (simulada), como el "acuerdo de pago" de PayPal: la
// persona aprueba una vez y las siguientes compras se cobran con un solo
// "Confirmar compra", sin volver a la ventana de PayPal.
//
// No guarda contraseñas ni tarjetas: solo qué cuenta autorizó y el id del
// acuerdo. Una por usuario; se quita desde "Mi cuenta".
@Document(collection = "vinculos_paypal")
public class VinculoPaypal {

    // El id del usuario: una cuenta vinculada por persona.
    @Id
    private String userId;
    private String cuenta;
    private String nombre;
    private String payerId;
    // Id del acuerdo de pago simulado (B-SIM-...).
    private String acuerdo;
    private Instant creadoEn;
    private Instant ultimoUsoEn;

    public VinculoPaypal() {}

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getCuenta() { return cuenta; }
    public void setCuenta(String cuenta) { this.cuenta = cuenta; }

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }

    public String getPayerId() { return payerId; }
    public void setPayerId(String payerId) { this.payerId = payerId; }

    public String getAcuerdo() { return acuerdo; }
    public void setAcuerdo(String acuerdo) { this.acuerdo = acuerdo; }

    public Instant getCreadoEn() { return creadoEn; }
    public void setCreadoEn(Instant creadoEn) { this.creadoEn = creadoEn; }

    public Instant getUltimoUsoEn() { return ultimoUsoEn; }
    public void setUltimoUsoEn(Instant ultimoUsoEn) { this.ultimoUsoEn = ultimoUsoEn; }
}
