package com.gymtrack.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDate;

@Document(collection = "users")
public class User {

    // Estados posibles de la membresía de un usuario frente a un gimnasio.
    public static final String STATUS_NONE = "none";         // sin gimnasio vinculado
    public static final String STATUS_PENDING = "pending";   // metió el código, falta que el gym lo apruebe
    public static final String STATUS_ACTIVE = "active";     // dado de alta, acceso completo
    public static final String STATUS_INACTIVE = "inactive"; // dado de baja por el gimnasio

    @Id
    private String id;
    private String nombre;
    private String email;
    private String password;
    private String gymId;
    // "owner" (administra su propio gimnasio desde el panel web)
    // o "member" (creó su cuenta en la app, o lo dio de alta un owner)
    private String role = "owner";
    // Solo aplica a members: false = dado de baja, pierde acceso a lo ligado al gimnasio.
    // Se mantiene por compatibilidad con los documentos ya guardados en Mongo;
    // membershipStatus es la fuente de verdad y ambos se escriben en sincronía.
    private Boolean membershipActive = true;
    private String membershipStatus;
    // Día del mes en que le toca pagar (1-31). Se fija con su primer pago.
    private Integer diaDePago;
    // Hasta cuándo está cubierta su mensualidad. El día siguiente a esta fecha
    // el cobrador automático lo pasa a "inactive".
    private LocalDate fechaProximoPago;
    // Token de Expo para mandarle notificaciones push a su teléfono.
    private String pushToken;

    public User() {}

    public User(String nombre, String email, String password) {
        this.nombre = nombre;
        this.email = email;
        this.password = password;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getGymId() { return gymId; }
    public void setGymId(String gymId) { this.gymId = gymId; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public Boolean getMembershipActive() { return membershipActive; }
    public void setMembershipActive(Boolean membershipActive) { this.membershipActive = membershipActive; }

    // Los usuarios creados antes de que existiera membershipStatus no lo tienen guardado:
    // se deduce a partir de gymId + membershipActive para no romper cuentas existentes.
    public String getMembershipStatus() {
        if (membershipStatus != null && !membershipStatus.isBlank()) return membershipStatus;
        if (gymId == null || gymId.isBlank()) return STATUS_NONE;
        return Boolean.FALSE.equals(membershipActive) ? STATUS_INACTIVE : STATUS_ACTIVE;
    }

    // Escribe el estado y mantiene membershipActive alineado (solo "active" da acceso).
    public void setMembershipStatus(String membershipStatus) {
        this.membershipStatus = membershipStatus;
        this.membershipActive = STATUS_ACTIVE.equals(membershipStatus);
    }

    public Integer getDiaDePago() { return diaDePago; }
    public void setDiaDePago(Integer diaDePago) { this.diaDePago = diaDePago; }

    public LocalDate getFechaProximoPago() { return fechaProximoPago; }
    public void setFechaProximoPago(LocalDate fechaProximoPago) { this.fechaProximoPago = fechaProximoPago; }

    public String getPushToken() { return pushToken; }
    public void setPushToken(String pushToken) { this.pushToken = pushToken; }

    // Días que faltan para la fecha de corte. Negativo = ya venció.
    // null cuando el gimnasio no le ha registrado ningún pago todavía.
    public Long diasParaVencer(LocalDate hoy) {
        if (fechaProximoPago == null) return null;
        return java.time.temporal.ChronoUnit.DAYS.between(hoy, fechaProximoPago);
    }

    // Único punto que decide si el usuario puede ver contenido del gimnasio.
    public boolean tieneAccesoAlGimnasio() {
        if (gymId == null || gymId.isBlank()) return false;
        if ("owner".equals(role)) return true;
        return STATUS_ACTIVE.equals(getMembershipStatus());
    }
}
