package com.gymtrack.controller;

import com.gymtrack.service.AccesoService;
import com.gymtrack.service.SimuladorStripeService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

// Simulador de Stripe.js: la tarjeta de prueba entra aquí y sale un token.
@RestController
@RequestMapping("/api/simuladores/stripe")
public class SimuladorStripeController {

    private final SimuladorStripeService stripe;
    private final AccesoService acceso;

    public SimuladorStripeController(SimuladorStripeService stripe, AccesoService acceso) {
        this.stripe = stripe;
        this.acceso = acceso;
    }

    // POST /api/simuladores/stripe/tokens
    //   {"numero": "4242 4242 4242 4242", "titular": "Ana", "mes": "12", "anio": "28", "cvc": "123"}
    // → {"token": "tok_sim_...", "marca": "visa", "ultimos4": "4242", "vencimiento": "12/28"}
    @PostMapping("/tokens")
    public Map<String, Object> tokenizar(@RequestHeader(value = AccesoService.ENCABEZADO, required = false) String userId,
                                         @RequestBody SimuladorStripeService.TarjetaRequest tarjeta) {
        return stripe.tokenizar(acceso.exigirUsuario(userId).getId(), tarjeta);
    }
}
