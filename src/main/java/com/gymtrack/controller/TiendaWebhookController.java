package com.gymtrack.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gymtrack.model.AvisoTienda;
import com.gymtrack.model.Pedido;
import com.gymtrack.repository.AvisoTiendaRepository;
import com.gymtrack.service.PedidoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;

// Avisos de Medusa (medusa/src/subscribers/avisar-pedidos.ts):
//   order.placed      → se completó un carrito
//   payment.captured  → se cobró un pago que estaba pendiente (Paynet)
//   order.canceled    → se canceló un pedido
//
// Llegan firmados con HMAC-SHA256 y MEDUSA_WEBHOOK_SECRET en el encabezado
//   X-GymTrack-Firma: t=<segundos unix>,v1=<hex de HMAC("t.cuerpo")>
// Con una firma inválida o de hace más de 5 minutos se responde 401 y no se toca nada.
//
// Idempotente: el mismo evento del mismo pedido se procesa una sola vez, y aun
// si se colara un repetido, PedidoService y BillingService no extienden dos
// veces la membresía (índice único de Payment.orderId).
@RestController
@RequestMapping("/api/tienda/webhooks")
public class TiendaWebhookController {

    private static final Logger log = LoggerFactory.getLogger(TiendaWebhookController.class);
    private static final long TOLERANCIA_SEGUNDOS = 300;
    private static final Set<String> EVENTOS = Set.of("order.placed", "payment.captured", "order.canceled");

    private final PedidoService pedidoService;
    private final AvisoTiendaRepository avisoRepository;
    private final ObjectMapper json;
    private final String secreto;

    public TiendaWebhookController(PedidoService pedidoService, AvisoTiendaRepository avisoRepository, ObjectMapper json,
                                   @Value("${tienda.medusa.webhook-secret:}") String secreto) {
        this.pedidoService = pedidoService;
        this.avisoRepository = avisoRepository;
        this.json = json;
        this.secreto = secreto == null ? "" : secreto.trim();
    }

    @PostMapping("/medusa")
    public ResponseEntity<?> recibir(@RequestHeader(value = "X-GymTrack-Firma", required = false) String firma,
                                     @RequestBody byte[] cuerpo) {
        if (secreto.isEmpty()) {
            log.error("Llegó un aviso de Medusa pero falta MEDUSA_WEBHOOK_SECRET en Spring.");
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of("error", "Webhook sin configurar."));
        }
        if (!firmaValida(firma, cuerpo)) {
            log.warn("Aviso de Medusa rechazado: firma inválida o vencida.");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Firma inválida."));
        }

        JsonNode aviso;
        try {
            aviso = json.readTree(cuerpo);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Aviso ilegible."));
        }
        String evento = aviso.path("evento").asText();
        String orderId = aviso.path("orderId").asText("");
        if (!EVENTOS.contains(evento) || orderId.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Evento no reconocido."));
        }

        // payment.captured se identifica por el pago: un pedido podría tener más de uno.
        String paymentId = aviso.path("paymentId").asText("");
        String llave = evento + ":" + (paymentId.isBlank() ? orderId : paymentId);
        if (avisoRepository.existsById(llave)) {
            return ResponseEntity.ok(Map.of("message", "Aviso ya procesado."));
        }

        // Si algo falla aquí, Medusa recibe un 5xx y reintenta: el aviso solo se
        // marca como procesado cuando el pedido ya quedó sincronizado.
        Pedido pedido = pedidoService.sincronizar(orderId);
        avisoRepository.save(new AvisoTienda(llave, evento, orderId));
        log.info("Aviso {} del pedido #{} procesado (estado: {}).", evento, pedido.getFolio(), pedido.getEstado());
        return ResponseEntity.ok(Map.of("message", "Aviso procesado.", "estado", pedido.getEstado()));
    }

    // La firma se calcula sobre los bytes tal como llegaron, sin reinterpretar el texto.
    private boolean firmaValida(String encabezado, byte[] cuerpo) {
        if (encabezado == null || cuerpo == null) return false;
        long timestamp = -1;
        String recibida = null;
        for (String parte : encabezado.split(",")) {
            String[] kv = parte.trim().split("=", 2);
            if (kv.length != 2) continue;
            if ("t".equals(kv[0])) {
                try { timestamp = Long.parseLong(kv[1]); } catch (NumberFormatException ignorada) { return false; }
            } else if ("v1".equals(kv[0])) {
                recibida = kv[1];
            }
        }
        if (timestamp < 0 || recibida == null) return false;
        if (Math.abs(Instant.now().getEpochSecond() - timestamp) > TOLERANCIA_SEGUNDOS) return false;

        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secreto.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            mac.update((timestamp + ".").getBytes(StandardCharsets.UTF_8));
            byte[] esperada = mac.doFinal(cuerpo);
            byte[] recibidaBytes = HexFormat.of().parseHex(recibida);
            // Comparación en tiempo constante: no revela cuántos caracteres coincidieron.
            return MessageDigest.isEqual(esperada, recibidaBytes);
        } catch (Exception e) {
            return false;
        }
    }
}
