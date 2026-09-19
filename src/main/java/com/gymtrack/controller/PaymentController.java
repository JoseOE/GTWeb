package com.gymtrack.controller;

import com.gymtrack.model.Payment;
import com.gymtrack.model.User;
import com.gymtrack.repository.PaymentRepository;
import com.gymtrack.repository.UserRepository;
import com.gymtrack.service.BillingService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

// Registro de mensualidades. El dueño cobra como quiera y lo marca aquí;
// eso es lo que reactiva la membresía y mueve la fecha de corte.
@RestController
@RequestMapping("/api/gyms/{gymId}/members/{userId}/payments")
public class PaymentController {

    private final UserRepository userRepository;
    private final PaymentRepository paymentRepository;
    private final BillingService billingService;

    public PaymentController(UserRepository userRepository, PaymentRepository paymentRepository,
                             BillingService billingService) {
        this.userRepository = userRepository;
        this.paymentRepository = paymentRepository;
        this.billingService = billingService;
    }

    @GetMapping
    public ResponseEntity<?> historial(@PathVariable String gymId, @PathVariable String userId) {
        return ResponseEntity.ok(paymentRepository.findByUserIdOrderByFechaPagoDesc(userId));
    }

    @PostMapping
    public ResponseEntity<?> registrarPago(
            @PathVariable String gymId,
            @PathVariable String userId,
            @RequestBody PagoRequest request
    ) {
        Optional<User> memberOpt = userRepository.findById(userId)
                .filter(u -> gymId.equals(u.getGymId()) && "member".equals(u.getRole()));
        if (memberOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Usuario no encontrado en este gimnasio."));
        }

        LocalDate fecha;
        try {
            fecha = request.getFechaPago() == null || request.getFechaPago().isBlank()
                    ? LocalDate.now()
                    : LocalDate.parse(request.getFechaPago());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Fecha de pago inválida (usa AAAA-MM-DD)."));
        }

        Payment pago = billingService.registrarPago(
                memberOpt.get(), gymId, request.getMonto(), request.getMetodo(), fecha, request.getNota());

        User actualizado = userRepository.findById(userId).orElseThrow();
        return ResponseEntity.ok(Map.of(
                "pago", pago,
                "fechaProximoPago", actualizado.getFechaProximoPago(),
                "membershipStatus", actualizado.getMembershipStatus()
        ));
    }

    public static class PagoRequest {
        private Double monto;
        private String metodo;
        private String fechaPago;
        private String nota;

        public Double getMonto() { return monto; }
        public void setMonto(Double monto) { this.monto = monto; }

        public String getMetodo() { return metodo; }
        public void setMetodo(String metodo) { this.metodo = metodo; }

        public String getFechaPago() { return fechaPago; }
        public void setFechaPago(String fechaPago) { this.fechaPago = fechaPago; }

        public String getNota() { return nota; }
        public void setNota(String nota) { this.nota = nota; }
    }
}
