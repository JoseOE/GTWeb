package com.gymtrack.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

// Un pago de un gimnasio a GymTrack por su suscripción: qué plan, cuánto y
// qué días cubre. Lo ve el dueño en su panel y el equipo en Suscripciones.
@Document(collection = "pagos_suscripcion")
public class PagoSuscripcion {

    @Id
    private String id;
    @Indexed
    private String gymId;
    private String gymNombre;
    private String userId;
    // "anual" o "mensual"
    private String plan;
    private double monto;
    private LocalDate desde;
    private LocalDate hasta;
    // stripe o paypal
    private String metodo;
    // Lo que deja el cobro: marca y últimos 4 de la tarjeta, cuenta de PayPal, operación.
    private Map<String, Object> datos = new LinkedHashMap<>();
    @Indexed
    private Instant pagadoEn;

    public PagoSuscripcion() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getGymId() { return gymId; }
    public void setGymId(String gymId) { this.gymId = gymId; }

    public String getGymNombre() { return gymNombre; }
    public void setGymNombre(String gymNombre) { this.gymNombre = gymNombre; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getPlan() { return plan; }
    public void setPlan(String plan) { this.plan = plan; }

    public double getMonto() { return monto; }
    public void setMonto(double monto) { this.monto = monto; }

    public LocalDate getDesde() { return desde; }
    public void setDesde(LocalDate desde) { this.desde = desde; }

    public LocalDate getHasta() { return hasta; }
    public void setHasta(LocalDate hasta) { this.hasta = hasta; }

    public String getMetodo() { return metodo; }
    public void setMetodo(String metodo) { this.metodo = metodo; }

    public Map<String, Object> getDatos() { return datos; }
    public void setDatos(Map<String, Object> datos) { this.datos = datos; }

    public Instant getPagadoEn() { return pagadoEn; }
    public void setPagadoEn(Instant pagadoEn) { this.pagadoEn = pagadoEn; }
}
