package com.gymtrack.controller;

import com.gymtrack.service.TiendaException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

// Todos los endpoints de la tienda (catálogo, carrito, pedidos, recibos...)
// responden sus errores con la misma forma, la que ya usa el resto de la API:
//   {"error": "mensaje para la persona"}
// y, si Medusa está despertando, además {"despertando": true} con Retry-After
// para que la web y la app reintenten solas. Algunos errores agregan datos
// (p. ej. "carrito" cuando el carrito cambió antes de pagar).
@RestControllerAdvice
public class TiendaExceptionHandler {

    @ExceptionHandler(TiendaException.class)
    public ResponseEntity<Map<String, Object>> manejar(TiendaException e) {
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("error", e.getMessage());
        cuerpo.putAll(e.getDatos());
        if (e.isDespertando()) {
            cuerpo.put("despertando", true);
            return ResponseEntity.status(e.getStatus()).header("Retry-After", "10").body(cuerpo);
        }
        return ResponseEntity.status(e.getStatus()).body(cuerpo);
    }
}
