package com.gymtrack.service;

import org.springframework.http.HttpStatus;

// Error esperado en la tienda: producto ajeno, datos incompletos, Medusa
// dormida... TiendaExceptionHandler lo convierte en {"error": mensaje}.
public class TiendaException extends RuntimeException {

    private final HttpStatus status;
    // true cuando Medusa no respondió a tiempo. En Render el plan gratuito la
    // duerme tras 15 minutos sin uso y tarda cerca de un minuto en despertar:
    // la página muestra "Despertando la tienda…" y reintenta sola.
    private final boolean despertando;

    public TiendaException(HttpStatus status, String mensaje) {
        this(status, mensaje, false);
    }

    private TiendaException(HttpStatus status, String mensaje, boolean despertando) {
        super(mensaje);
        this.status = status;
        this.despertando = despertando;
    }

    public static TiendaException despertando() {
        return new TiendaException(HttpStatus.SERVICE_UNAVAILABLE,
                "Despertando la tienda… Esto puede tardar hasta un minuto la primera vez.", true);
    }

    public HttpStatus getStatus() { return status; }

    public boolean isDespertando() { return despertando; }
}
