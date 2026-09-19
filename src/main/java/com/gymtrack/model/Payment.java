package com.gymtrack.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDate;

// Un pago de mensualidad registrado por el dueño del gimnasio.
// No hay pasarela: el gimnasio cobra como quiera y aquí queda el registro,
// que es lo que mueve la fecha de corte del usuario.
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
}
