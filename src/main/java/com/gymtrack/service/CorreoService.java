package com.gymtrack.service;

import com.gymtrack.model.User;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

// Arma los correos de la cuenta con las plantillas de templates/correos y los
// manda por SMTP (Gmail). Si el correo no está configurado en el .env, la página
// sigue funcionando: el aviso se escribe en la consola en lugar de enviarse.
@Service
public class CorreoService {

    private static final Logger log = LoggerFactory.getLogger(CorreoService.class);
    private static final Locale ES_MX = Locale.forLanguageTag("es-MX");
    private static final ZoneId ZONA_MX = ZoneId.of("America/Mexico_City");

    private final JavaMailSender mailSender;
    private final TemplateEngine plantillas;
    private final String remitente;
    private final String urlPublica;

    public CorreoService(JavaMailSender mailSender, TemplateEngine plantillas,
                         @Value("${spring.mail.username:}") String remitente,
                         @Value("${app.url-publica:http://localhost:8080}") String urlPublica) {
        this.mailSender = mailSender;
        this.plantillas = plantillas;
        this.remitente = remitente == null ? "" : remitente.trim();
        this.urlPublica = urlPublica.replaceAll("/+$", "");
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

    // Un correo que falla nunca tumba la operación que lo pidió: se avisa en la
    // consola y quien llama decide qué decirle al usuario.
    boolean enviar(String para, String asunto, String plantilla, Map<String, Object> variables) {
        if (!configurado()) {
            log.warn("Correo sin configurar (falta MAIL_USERNAME en el .env): no se envió \"{}\" a {}", asunto, para);
            return false;
        }
        try {
            Map<String, Object> datos = new HashMap<>(variables);
            datos.put("urlPublica", urlPublica);
            String html = plantillas.process("correos/" + plantilla, new Context(ES_MX, datos));

            MimeMessage mensaje = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mensaje, "UTF-8");
            helper.setFrom(remitente, "GymTrack");
            helper.setTo(para);
            helper.setSubject(asunto);
            helper.setText(html, true);
            mailSender.send(mensaje);
            log.info("Correo \"{}\" enviado a {}", asunto, para);
            return true;
        } catch (Exception e) {
            log.warn("No se pudo enviar \"{}\" a {}: {}", asunto, para, e.getMessage());
            return false;
        }
    }

    private boolean configurado() {
        return !remitente.isBlank();
    }

    // Fecha y hora del centro de México, p. ej. "19 de septiembre de 2026 a las 14:05".
    private static String ahora() {
        return ZonedDateTime.now(ZONA_MX).format(DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy 'a las' HH:mm", ES_MX));
    }

    // "Hola, Juan" con el primer nombre; "Hola" si la cuenta no tiene nombre.
    private String saludo(User user) {
        String nombre = user.getNombre() == null ? "" : user.getNombre().trim();
        return nombre.isEmpty() ? "Hola" : "Hola, " + nombre.split("\\s+")[0];
    }
}
