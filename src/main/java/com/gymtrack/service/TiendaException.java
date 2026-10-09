package com.gymtrack.service;

import org.springframework.http.HttpStatus;

import java.util.Map;

// Error esperado en la tienda: producto ajeno, datos incompletos, sin
// piezas... TiendaExceptionHandler lo convierte en {"error": mensaje}.
public class TiendaException extends RuntimeException {

    private final HttpStatus status;
    // Datos extra que viajan junto al error, p. ej. el carrito ya corregido
    // cuando un precio cambió justo antes de pagar.
    private final Map<String, Object> datos;

    public TiendaException(HttpStatus status, String mensaje) {
        this(status, mensaje, Map.of());
    }

    public TiendaException(HttpStatus status, String mensaje, Map<String, Object> datos) {
        super(mensaje);
        this.status = status;
        this.datos = datos;
    }

    public HttpStatus getStatus() { return status; }

    public Map<String, Object> getDatos() { return datos; }
}
