package com.gymtrack.controller;

import com.gymtrack.model.Payment;
import com.gymtrack.model.User;
import com.gymtrack.repository.PaymentRepository;
import com.gymtrack.repository.UserRepository;
import com.gymtrack.service.AccesoService;
import com.gymtrack.service.AvisosPedidoService;
import com.gymtrack.service.BillingService;
import com.gymtrack.service.CatalogoService;
import com.gymtrack.service.PlanPagado;
import com.gymtrack.service.TiendaException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

// Registro de mensualidades. El dueño cobra como quiera y lo marca aquí;
// eso es lo que reactiva la membresía y mueve la fecha de corte. Si elige uno
// de sus planes, la fecha se mueve según la duración del plan (sin plan, un mes).
@RestController
@RequestMapping("/api/gyms/{gymId}")
public class PaymentController {

    private final UserRepository userRepository;
    private final PaymentRepository paymentRepository;
    private final BillingService billingService;
    private final CatalogoService catalogo;
    private final AvisosPedidoService avisos;
    private final AccesoService acceso;

    public PaymentController(UserRepository userRepository, PaymentRepository paymentRepository,
                             BillingService billingService, CatalogoService catalogo,
                             AvisosPedidoService avisos, AccesoService acceso) {
        this.userRepository = userRepository;
        this.paymentRepository = paymentRepository;
        this.billingService = billingService;
        this.catalogo = catalogo;
        this.avisos = avisos;
        this.acceso = acceso;
    }

    // Los pagos de todo el gimnasio, del más reciente al más viejo, con el
    // nombre de cada miembro: "Últimos pagos" y lo cobrado en el mes salen de
    // aquí en una sola consulta, no de una por miembro.
    // GET /payments?limite=12  ·  GET /payments?desde=2026-10-01&limite=500
    @GetMapping("/payments")
    public List<Map<String, Object>> pagosDelGimnasio(
            @PathVariable String gymId,
            @RequestHeader(value = AccesoService.ENCABEZADO, required = false) String duenoId,
            @RequestParam(required = false) String desde,
            @RequestParam(defaultValue = "12") int limite) {
        acceso.exigirDueno(gymId, duenoId);
        PageRequest pagina = PageRequest.of(0, Math.max(1, Math.min(limite, 500)),
                Sort.by(Sort.Order.desc("fechaPago"), Sort.Order.desc("id")));
        List<Payment> pagos;
        if (desde == null || desde.isBlank()) {
            pagos = paymentRepository.findByGymId(gymId, pagina);
        } else {
            try {
                pagos = paymentRepository.findByGymIdAndFechaPagoGreaterThanEqual(gymId, LocalDate.parse(desde), pagina);
            } catch (DateTimeParseException e) {
                throw new TiendaException(HttpStatus.BAD_REQUEST, "Fecha inválida (usa AAAA-MM-DD).");
            }
        }

        Map<String, String> nombres = new HashMap<>();
        userRepository.findAllById(pagos.stream().map(Payment::getUserId).distinct().toList())
                .forEach(u -> nombres.put(u.getId(), u.getNombre()));
        return pagos.stream().map(p -> {
            Map<String, Object> fila = new LinkedHashMap<>();
            fila.put("id", p.getId());
            fila.put("userId", p.getUserId());
            fila.put("nombre", nombres.getOrDefault(p.getUserId(), "Usuario eliminado"));
            fila.put("fechaPago", p.getFechaPago());
            fila.put("monto", p.getMonto());
            fila.put("metodo", p.getMetodo());
            fila.put("plan", p.getPlan());
            fila.put("cubreHasta", p.getCubreHasta());
            // Con pedido: lo pagó en la tienda o en el mostrador (ya cuenta en Ventas).
            fila.put("orderId", p.getOrderId());
            fila.put("reembolsadoEn", p.getReembolsadoEn());
            return fila;
        }).toList();
    }

    @GetMapping("/members/{userId}/payments")
    public ResponseEntity<?> historial(@PathVariable String gymId, @PathVariable String userId) {
        return ResponseEntity.ok(paymentRepository.findByUserIdOrderByFechaPagoDesc(userId));
    }

    @PostMapping("/members/{userId}/payments")
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

        // La duración se lee del plan guardado en la tienda, no de lo que mande la página.
        PlanPagado plan = request.getPlanId() == null || request.getPlanId().isBlank()
                ? PlanPagado.MENSUAL
                : catalogo.planParaPago(gymId, request.getPlanId());

        Payment pago = billingService.registrarPago(
                memberOpt.get(), gymId, request.getMonto(), request.getMetodo(), fecha, request.getNota(), plan, null);
        // El miembro recibe su recibo en PDF por correo (en segundo plano).
        avisos.pagoRegistrado(pago);

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
        // Plan de la tienda que se está cobrando (opcional).
        private String planId;

        public Double getMonto() { return monto; }
        public void setMonto(Double monto) { this.monto = monto; }

        public String getMetodo() { return metodo; }
        public void setMetodo(String metodo) { this.metodo = metodo; }

        public String getFechaPago() { return fechaPago; }
        public void setFechaPago(String fechaPago) { this.fechaPago = fechaPago; }

        public String getNota() { return nota; }
        public void setNota(String nota) { this.nota = nota; }

        public String getPlanId() { return planId; }
        public void setPlanId(String planId) { this.planId = planId; }
    }
}
