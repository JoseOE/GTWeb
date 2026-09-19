package com.gymtrack.controller;

import com.gymtrack.service.BillingService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/billing")
public class BillingController {

    private final BillingService billingService;

    public BillingController(BillingService billingService) {
        this.billingService = billingService;
    }

    // POST /api/billing/run → corre la revisión de vencimientos en el momento.
    // El cron ya la ejecuta cada día a las 6:00; esto existe para que el dueño
    // pueda forzarla desde el panel y para poder probarla sin esperar un día.
    @PostMapping("/run")
    public ResponseEntity<?> revisarAhora() {
        billingService.revisarMembresias();
        return ResponseEntity.ok(Map.of("message", "Revisión de membresías ejecutada."));
    }
}
