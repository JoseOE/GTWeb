package com.gymtrack.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.time.LocalDate;

// Un pago de membresía. Llega de dos formas: el dueño lo registra a mano
// desde el panel (cobró como quiera) o lo genera una compra pagada en la
// tienda. En ambos casos es lo que mueve la fecha de corte del usuario.
@Document(collection = "payments")
public class Payment {

    @Id
    private String id;
    private String gymId;
    private String userId;
    private Double monto;
    private String metodo;
    private LocalDate fechaPago;
    // Hasta cuándo queda cubierta la membresía con este pago
    private LocalDate cubreHasta;
    private String nota;

    // ─── Plan ───
    // Plan (producto "membresia") que se pagó (null en los pagos anteriores a la tienda,
    // que siempre fueron de un mes).
    private String planId;
    private String plan;
    private String duracionUnidad;
    private Integer duracionCantidad;
    // Pedido de la tienda que originó el pago. Único: es lo que impide que un
    // pedido confirmado dos veces extienda la membresía dos veces.
    @Indexed(unique = true, sparse = true)
    private String orderId;
    // Fecha de corte que tenía el miembro justo antes de este pago (null si no
    // tenía). Si el pedido se reembolsa, la membresía vuelve a ella.
    private LocalDate corteAnterior;
    // Cuándo se reembolsó el pedido de la tienda que originó el pago.
    private Instant reembolsadoEn;

    public Payment() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getGymId() { return gymId; }
    public void setGymId(String gymId) { this.gymId = gymId; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public Double getMonto() { return monto; }
    public void setMonto(Double monto) { this.monto = monto; }

    public String getMetodo() { return metodo; }
    public void setMetodo(String metodo) { this.metodo = metodo; }

    public LocalDate getFechaPago() { return fechaPago; }
    public void setFechaPago(LocalDate fechaPago) { this.fechaPago = fechaPago; }

    public LocalDate getCubreHasta() { return cubreHasta; }
    public void setCubreHasta(LocalDate cubreHasta) { this.cubreHasta = cubreHasta; }

    public String getNota() { return nota; }
    public void setNota(String nota) { this.nota = nota; }

    public String getPlanId() { return planId; }
    public void setPlanId(String planId) { this.planId = planId; }

    public String getPlan() { return plan; }
    public void setPlan(String plan) { this.plan = plan; }

    public String getDuracionUnidad() { return duracionUnidad; }
    public void setDuracionUnidad(String duracionUnidad) { this.duracionUnidad = duracionUnidad; }

    public Integer getDuracionCantidad() { return duracionCantidad; }
    public void setDuracionCantidad(Integer duracionCantidad) { this.duracionCantidad = duracionCantidad; }

    public String getOrderId() { return orderId; }
    public void setOrderId(String orderId) { this.orderId = orderId; }

    public LocalDate getCorteAnterior() { return corteAnterior; }
    public void setCorteAnterior(LocalDate corteAnterior) { this.corteAnterior = corteAnterior; }

    public Instant getReembolsadoEn() { return reembolsadoEn; }
    public void setReembolsadoEn(Instant reembolsadoEn) { this.reembolsadoEn = reembolsadoEn; }
}
