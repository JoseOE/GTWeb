package com.gymtrack.service;

import com.gymtrack.model.EnlaceRecibo;
import com.gymtrack.model.Gym;
import com.gymtrack.model.Pedido;
import com.gymtrack.repository.EnlaceReciboRepository;
import com.gymtrack.repository.GymRepository;
import com.gymtrack.repository.PedidoRepository;
import com.gymtrack.util.MetodosPago;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

// Enlaces temporales para compartir un comprobante por WhatsApp (o por donde
// sea): WhatsApp no recibe archivos desde una página de escritorio, así que se
// manda un enlace que descarga el PDF sin iniciar sesión.
//
//   - Solo lo genera quien puede ver el pedido (el comprador o el dueño).
//   - Vence a los 7 días y Mongo lo borra solo.
//   - Pedido pagado o reembolsado → su comprobante; pendiente de pago en
//     tienda → su ficha con el código de barras. Cancelado → no hay enlace.
@Service
public class EnlaceReciboService {

    public static final Duration VIGENCIA = Duration.ofDays(7);
    private static final SecureRandom AZAR = new SecureRandom();
    private static final ZoneId ZONA_MX = ZoneId.of("America/Mexico_City");
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("d 'de' MMMM", Locale.forLanguageTag("es-MX"));

    // Lo que descarga un enlace: el PDF y el nombre del archivo.
    public record Archivo(byte[] pdf, String nombre) {}

    private final EnlaceReciboRepository enlaces;
    private final PedidoRepository pedidos;
    private final GymRepository gyms;
    private final ReciboService recibos;
    private final String urlPublica;

    public EnlaceReciboService(EnlaceReciboRepository enlaces, PedidoRepository pedidos, GymRepository gyms,
                               ReciboService recibos, @Value("${app.url-publica:http://localhost:8080}") String urlPublica) {
        this.enlaces = enlaces;
        this.pedidos = pedidos;
        this.gyms = gyms;
        this.recibos = recibos;
        this.urlPublica = urlPublica.replaceAll("/+$", "");
    }

    // Crea el enlace y el mensaje listo para mandar. Quien llama ya revisó que
    // userId puede ver el pedido.
    public Map<String, Object> crear(Pedido p, String userId) {
        boolean ficha = esFicha(p);
        if (!ficha && !conRecibo(p)) {
            throw new TiendaException(HttpStatus.CONFLICT, "Este pedido se canceló y no tiene comprobante para compartir.");
        }
        byte[] bytes = new byte[32];
        AZAR.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Instant ahora = Instant.now();
        Instant vence = ahora.plus(VIGENCIA);
        enlaces.save(new EnlaceRecibo(SesionService.huella(token), p.getOrderId(), userId, ahora, vence));

        String url = urlPublica + "/api/recibos/compartidos/" + token + ".pdf";
        String gym = gyms.findById(p.getGymId() == null ? "" : p.getGymId()).map(Gym::getNombre).orElse("GymTrack");
        String texto = (ficha ? "Ficha de pago del pedido #" : "Comprobante del pedido #") + p.getFolio()
                + " en " + gym + " por " + CarritoService.dinero(p.getTotal() == null ? 0 : p.getTotal()) + ": " + url
                + " (el enlace vence el " + FECHA.format(vence.atZone(ZONA_MX)) + ")";

        Map<String, Object> r = new LinkedHashMap<>();
        r.put("url", url);
        r.put("venceEn", vence.toString());
        r.put("texto", texto);
        r.put("tipo", ficha ? "ficha" : "comprobante");
        return r;
    }

    // El PDF de un enlace vigente. Si no existe o venció, 404 sin decir cuál.
    public Archivo abrir(String token) {
        EnlaceRecibo enlace = token == null || token.isBlank() ? null
                : enlaces.findById(SesionService.huella(token.trim())).orElse(null);
        if (enlace == null || enlace.getVenceEn() == null || !enlace.getVenceEn().isAfter(Instant.now())) {
            throw new TiendaException(HttpStatus.NOT_FOUND, "Este enlace ya venció o no existe. Pide que te lo compartan de nuevo.");
        }
        Pedido p = pedidos.findByOrderId(enlace.getOrderId())
                .orElseThrow(() -> new TiendaException(HttpStatus.NOT_FOUND, "Este enlace ya venció o no existe. Pide que te lo compartan de nuevo."));
        // El estado se revisa al abrir: si la ficha ya se pagó, sale el comprobante.
        if (conRecibo(p)) return new Archivo(recibos.reciboPedido(p), "recibo-" + p.getFolio() + ".pdf");
        if (esFicha(p)) return new Archivo(recibos.fichaPaynet(p), "ficha-pago-" + p.getFolio() + ".pdf");
        throw new TiendaException(HttpStatus.GONE, "Este pedido se canceló y ya no tiene comprobante.");
    }

    private static boolean conRecibo(Pedido p) {
        return Pedido.ESTADO_PAGADO.equals(p.getEstado()) || Pedido.ESTADO_REEMBOLSADO.equals(p.getEstado());
    }

    private static boolean esFicha(Pedido p) {
        return Pedido.ESTADO_PENDIENTE_PAGO.equals(p.getEstado()) && MetodosPago.PAYNET.equals(p.getProveedorPago());
    }
}
