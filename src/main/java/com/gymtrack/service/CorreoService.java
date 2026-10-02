package com.gymtrack.service;

import com.gymtrack.model.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

// Arma los correos de la cuenta con las plantillas de templates/correos y los
// manda por la API HTTP de Brevo (no por SMTP: Render bloquea los puertos SMTP
// salientes en todos sus planes). Si el correo no está configurado en el .env,
// la página sigue funcionando: el aviso se escribe en la consola en lugar de enviarse.
@Service
public class CorreoService {

    private static final Logger log = LoggerFactory.getLogger(CorreoService.class);
    private static final Locale ES_MX = Locale.forLanguageTag("es-MX");
    private static final ZoneId ZONA_MX = ZoneId.of("America/Mexico_City");

    private final RestClient brevo;
    private final TemplateEngine plantillas;
    private final String apiKey;
    private final String remitente;
    private final String urlPublica;

    public CorreoService(TemplateEngine plantillas,
                         @Value("${brevo.api-key:}") String apiKey,
                         @Value("${brevo.remitente:}") String remitente,
                         @Value("${app.url-publica:http://localhost:8080}") String urlPublica) {
        this.plantillas = plantillas;
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.remitente = remitente == null ? "" : remitente.trim();
        this.urlPublica = urlPublica.replaceAll("/+$", "");
        this.brevo = RestClient.builder()
                .baseUrl("https://api.brevo.com/v3")
                .defaultHeader("api-key", this.apiKey)
                .defaultHeader("accept", "application/json")
                .build();
    }

    // Enlace absoluto a una página del sitio, para los botones de los correos.
    public String enlace(String ruta) {
        return urlPublica + "/" + ruta;
    }

    public boolean verificacion(User user, String codigo, String enlace, long minutos) {
        if (!configurado()) log.warn("Correo sin configurar: el código de verificación de {} es {}", user.getEmail(), codigo);
        return enviar(user.getEmail(), "Tu código de verificación: " + codigo, "verificacion", Map.of(
                "saludo", saludo(user), "codigo", codigo, "enlace", enlace, "minutos", minutos));
    }

    public boolean bienvenida(User user) {
        return enviar(user.getEmail(), "¡Bienvenido a GymTrack!", "bienvenida", Map.of(
                "saludo", saludo(user), "esMiembro", "member".equals(user.getRole()), "enlace", enlace("login.html")));
    }

    public boolean recuperarContrasena(User user, String enlace, long minutos) {
        if (!configurado()) log.warn("Correo sin configurar: el enlace para restablecer la contraseña de {} es {}", user.getEmail(), enlace);
        return enviar(user.getEmail(), "Restablece tu contraseña de GymTrack", "recuperar-contrasena", Map.of(
                "saludo", saludo(user), "enlace", enlace, "minutos", minutos));
    }

    public boolean contrasenaCambiada(User user) {
        return enviar(user.getEmail(), "Tu contraseña de GymTrack cambió", "contrasena-cambiada", Map.of(
                "saludo", saludo(user), "fecha", ahora(), "enlace", enlace("recuperar.html")));
    }

    public boolean codigoCambioDeCorreo(User user, String nuevoCorreo, String codigo, long minutos) {
        if (!configurado()) log.warn("Correo sin configurar: el código para cambiar el correo de {} a {} es {}", user.getEmail(), nuevoCorreo, codigo);
        return enviar(nuevoCorreo, "Tu código para confirmar tu correo: " + codigo, "cambio-correo-codigo", Map.of(
                "saludo", saludo(user), "codigo", codigo, "minutos", minutos, "nuevo", nuevoCorreo));
    }

    // Va al correo ANTERIOR: si el cambio no lo hizo el dueño, así se entera.
    public boolean correoCambiado(User user, String correoAnterior) {
        return enviar(correoAnterior, "El correo de tu cuenta de GymTrack cambió", "correo-cambiado", Map.of(
                "saludo", saludo(user), "nuevo", user.getEmail(), "fecha", ahora()));
    }

    // ─── Avisos de la membresía (acompañan al push de la app) ───

    public boolean miembroAprobado(User member, String nombreGym) {
        return enviar(member.getEmail(), "¡Bienvenido a " + nombreGym + "!", "miembro-aprobado", Map.of(
                "saludo", saludo(member), "gimnasio", nombreGym));
    }

    public boolean pagoPorVencer(User member, String nombreGym, LocalDate fecha, long dias) {
        return enviar(member.getEmail(), "Tu mensualidad en " + nombreGym + " vence en " + dias + " días", "pago-por-vencer", Map.of(
                "saludo", saludo(member), "gimnasio", nombreGym, "fecha", fecha(fecha), "dias", dias));
    }

    public boolean membresiaVencida(User member, String nombreGym) {
        return enviar(member.getEmail(), "Tu membresía en " + nombreGym + " venció", "membresia-vencida", Map.of(
                "saludo", saludo(member), "gimnasio", nombreGym));
    }

    // ─── Tienda y recibos (llevan el PDF adjunto) ───

    public boolean compraConfirmada(User comprador, String nombreGym, Map<String, Object> pedido, byte[] recibo) {
        return enviar(comprador.getEmail(), "Tu compra en " + nombreGym + " · pedido #" + pedido.get("folio"), "compra-confirmada",
                Map.of("saludo", saludo(comprador), "gimnasio", nombreGym, "pedido", pedido,
                        "enlace", enlace("confirmacion.html?pedido=" + pedido.get("orderId"))),
                List.of(new Adjunto("recibo-" + pedido.get("folio") + ".pdf", recibo)));
    }

    public boolean fichaPaynet(User comprador, String nombreGym, Map<String, Object> pedido, String referencia,
                               String vence, byte[] ficha) {
        return enviar(comprador.getEmail(), "Tu ficha de pago Paynet · pedido #" + pedido.get("folio"), "ficha-paynet",
                Map.of("saludo", saludo(comprador), "gimnasio", nombreGym, "pedido", pedido, "referencia", referencia,
                        "vence", vence, "enlace", enlace("confirmacion.html?pedido=" + pedido.get("orderId"))),
                List.of(new Adjunto("ficha-paynet-" + pedido.get("folio") + ".pdf", ficha)));
    }

    public boolean pagoPaynetRecibido(User comprador, String nombreGym, Map<String, Object> pedido, byte[] recibo) {
        return enviar(comprador.getEmail(), "Recibimos tu pago · pedido #" + pedido.get("folio"), "pago-paynet-recibido",
                Map.of("saludo", saludo(comprador), "gimnasio", nombreGym, "pedido", pedido,
                        "enlace", enlace("confirmacion.html?pedido=" + pedido.get("orderId"))),
                List.of(new Adjunto("recibo-" + pedido.get("folio") + ".pdf", recibo)));
    }

    public boolean reciboMensualidad(User member, String nombreGym, String concepto, double monto, LocalDate cubreHasta,
                                     String folio, byte[] recibo) {
        return enviar(member.getEmail(), "Tu recibo de " + nombreGym, "recibo-mensualidad",
                Map.of("saludo", saludo(member), "gimnasio", nombreGym, "concepto", concepto,
                        "monto", String.format(ES_MX, "$%,.2f", monto),
                        "cubreHasta", cubreHasta == null ? "" : fecha(cubreHasta)),
                List.of(new Adjunto("recibo-" + folio + ".pdf", recibo)));
    }

    // Archivo adjunto: Brevo lo recibe en base64.
    public record Adjunto(String nombre, byte[] contenido) {}

    // false = falta MAIL_USERNAME en el .env; los códigos se escriben en la consola.
    public boolean estaConfigurado() {
        return configurado();
    }

    boolean enviar(String para, String asunto, String plantilla, Map<String, Object> variables) {
        return enviar(para, asunto, plantilla, variables, List.of());
    }

    // Un correo que falla nunca tumba la operación que lo pidió: se avisa en la
    // consola y quien llama decide qué decirle al usuario.
    boolean enviar(String para, String asunto, String plantilla, Map<String, Object> variables, List<Adjunto> adjuntos) {
        if (!configurado()) {
            // La plantilla se arma de todos modos: así un error en ella aparece
            // en la consola también en local, sin Brevo.
            try {
                html(plantilla, variables);
            } catch (Exception e) {
                log.error("La plantilla de correo \"{}\" tiene un error: {}", plantilla, e.getMessage());
                return false;
            }
            log.warn("Correo sin configurar (falta BREVO_API_KEY o MAIL_USERNAME en el .env): no se envió \"{}\" a {}{}", asunto, para,
                    adjuntos.isEmpty() ? "" : " (" + adjuntos.size() + " adjunto(s))");
            return false;
        }
        try {
            String html = html(plantilla, variables);

            Map<String, Object> cuerpo = new HashMap<>(Map.of(
                    "sender", Map.of("name", "GymTrack", "email", remitente),
                    "to", List.of(Map.of("email", para)),
                    "subject", asunto,
                    "htmlContent", html));
            if (!adjuntos.isEmpty()) {
                cuerpo.put("attachment", adjuntos.stream()
                        .map(a -> Map.of("name", a.nombre(), "content", Base64.getEncoder().encodeToString(a.contenido())))
                        .toList());
            }

            brevo.post()
                    .uri("/smtp/email")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(cuerpo)
                    .retrieve()
                    .toBodilessEntity();

            log.info("Correo \"{}\" enviado a {}", asunto, para);
            return true;
        } catch (Exception e) {
            log.warn("No se pudo enviar \"{}\" a {}: {}", asunto, para, e.getMessage());
            return false;
        }
    }

    private String html(String plantilla, Map<String, Object> variables) {
        Map<String, Object> datos = new HashMap<>(variables);
        datos.put("urlPublica", urlPublica);
        return plantillas.process("correos/" + plantilla, new Context(ES_MX, datos));
    }

    private boolean configurado() {
        return !apiKey.isBlank() && !remitente.isBlank();
    }

    // Fecha y hora del centro de México, p. ej. "19 de septiembre de 2026 a las 14:05".
    private static String ahora() {
        return ZonedDateTime.now(ZONA_MX).format(DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy 'a las' HH:mm", ES_MX));
    }

    // "24 de septiembre de 2026"
    private static String fecha(LocalDate fecha) {
        return fecha.format(DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy", ES_MX));
    }

    // "Hola, Juan" con el primer nombre; "Hola" si la cuenta no tiene nombre.
    private String saludo(User user) {
        String nombre = user.getNombre() == null ? "" : user.getNombre().trim();
        return nombre.isEmpty() ? "Hola" : "Hola, " + nombre.split("\\s+")[0];
    }
}
