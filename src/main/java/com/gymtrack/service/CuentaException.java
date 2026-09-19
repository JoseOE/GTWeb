package com.gymtrack.service;

import org.springframework.http.HttpStatus;

// Error esperado en un trámite de la cuenta (código incorrecto, enlace vencido,
// esperar para reenviar…). El controlador lo convierte en {"error": mensaje}.
public class CuentaException extends RuntimeException {

    private final HttpStatus status;

    public CuentaException(HttpStatus status, String mensaje) {
        super(mensaje);
        this.status = status;
    }

    public HttpStatus getStatus() { return status; }
}
