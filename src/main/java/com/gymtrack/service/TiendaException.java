package com.gymtrack.service;

import org.springframework.http.HttpStatus;

import java.util.Map;

// Error esperado en la tienda: producto ajeno, datos incompletos, Medusa
// dormida... TiendaExceptionHandler lo convierte en {"error": mensaje}.
public class TiendaException extends RuntimeException {

    private final HttpStatus status;
    // true cuando Medusa no respondió a tiempo. En Render el plan gratuito la
    // duerme tras 15 minutos sin uso y tarda cerca de un minuto en despertar:
    // la página muestra "Despertando la tienda…" y reintenta sola.
    private final boolean despertando;
    // Datos extra que viajan junto al error, p. ej. el carrito ya corregido
    // cuando un precio cambió justo antes de pagar.
    private final Map<String, Object> datos;

    public TiendaException(HttpStatus status, String mensaje) {
        this(status, mensaje, false, Map.of());
    }

    public TiendaException(HttpStatus status, String mensaje, Map<String, Object> datos) {
        this(status, mensaje, false, datos);
    }

    private TiendaException(HttpStatus status, String mensaje, boolean despertando, Map<String, Object> datos) {
        super(mensaje);
        this.status = status;
        this.despertando = despertando;
        this.datos = datos;
    }

    public static TiendaException despertando() {
        return new TiendaException(HttpStatus.SERVICE_UNAVAILABLE,
                "Despertando la tienda… Esto puede tardar hasta un minuto la primera vez.", true, Map.of());
    }

    public HttpStatus getStatus() { return status; }

    public boolean isDespertando() { return despertando; }

    public Map<String, Object> getDatos() { return datos; }
}
