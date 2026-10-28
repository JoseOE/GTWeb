package com.gymtrack.service;

import com.gymtrack.model.EventoSeguridad;
import com.gymtrack.repository.EventoSeguridadRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// Bitácora de seguridad (ISO 27001, A.12.4.1 y A.12.4.3): inicios de sesión,
// cambios de contraseña y correo, accesos negados y lo que el dueño hace en su
// panel (miembros, pagos, reembolsos, cancelaciones, entregas, precios). Las
// acciones del panel las anota BitacoraFilter; los inicios de sesión y la
// recuperación de contraseña, quien los atiende.
//
// Anotar nunca hace fallar la acción: si Mongo no responde, queda en el log.
// Nunca se guardan contraseñas, tokens ni datos de pago.
@Service
public class BitacoraService {

    private static final Logger log = LoggerFactory.getLogger(BitacoraService.class);

    public static final String LOGIN_EXITOSO = "LOGIN_EXITOSO";
    public static final String LOGIN_FALLIDO = "LOGIN_FALLIDO";
    public static final String LOGIN_BLOQUEADO = "LOGIN_BLOQUEADO";
    public static final String CONTRASENA_RESTABLECIDA = "CONTRASENA_RESTABLECIDA";
    public static final String ACCESO_DENEGADO = "ACCESO_DENEGADO";

    // Para la tabla del panel.
    static final Map<String, String> DESCRIPCIONES = Map.ofEntries(
            Map.entry(LOGIN_EXITOSO, "Inicio de sesión"),
            Map.entry(LOGIN_FALLIDO, "Contraseña incorrecta"),
            Map.entry(LOGIN_BLOQUEADO, "Inicio de sesión frenado por demasiados intentos"),
            Map.entry(CONTRASENA_RESTABLECIDA, "Contraseña restablecida con el correo"),
            Map.entry(ACCESO_DENEGADO, "Acceso negado"),
            Map.entry("CONTRASENA_CAMBIADA", "Contraseña cambiada"),
            Map.entry("CORREO_CAMBIADO", "Correo de la cuenta cambiado"),
            Map.entry("GIMNASIO_CREADO", "Gimnasio creado"),
            Map.entry("CODIGO_GIMNASIO", "Código de invitación regenerado"),
            Map.entry("MIEMBRO_ALTA", "Miembro dado de alta"),
            Map.entry("MIEMBRO_ESTADO", "Acceso de un miembro cambiado"),
            Map.entry("MIEMBRO_BAJA", "Miembro desvinculado"),
            Map.entry("PAGO_REGISTRADO", "Pago de membresía registrado"),
            Map.entry("VENTA_MOSTRADOR", "Venta en mostrador"),
            Map.entry("PEDIDO_REEMBOLSADO", "Pedido reembolsado"),
            Map.entry("PEDIDO_CANCELADO", "Pedido cancelado"),
            Map.entry("PEDIDO_ENTREGADO", "Pedido entregado"),
            Map.entry("PRODUCTO_CAMBIADO", "Producto creado, editado o borrado"),
            Map.entry("PLAN_CAMBIADO", "Plan creado, editado o borrado"));

    private final EventoSeguridadRepository eventos;
    private final MongoTemplate mongo;

    public BitacoraService(EventoSeguridadRepository eventos, MongoTemplate mongo) {
        this.eventos = eventos;
        this.mongo = mongo;
    }

    // Lo que se sabe de la petición en curso (método, ruta, IP y navegador).
    public record Origen(String metodo, String ruta, String ip, String agente) {
        public static Origen de(HttpServletRequest r) {
            // En Render la IP real del cliente es la primera de X-Forwarded-For.
            String reenviada = r.getHeader("X-Forwarded-For");
            String ip = reenviada != null && !reenviada.isBlank() ? reenviada.split(",")[0].trim() : r.getRemoteAddr();
            return new Origen(r.getMethod(), r.getRequestURI(), recortar(ip, 64), recortar(r.getHeader("User-Agent"), 200));
        }

        static Origen actual() {
            return RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes a
                    ? de(a.getRequest()) : new Origen(null, null, null, null);
        }
    }

    // Desde un servicio o controlador, durante la petición.
    public void registrar(String tipo, String userId, String email, String gymId) {
        registrar(tipo, userId, email, gymId, null, Origen.actual());
    }

    // En segundo plano: la respuesta no espera a que se escriba el renglón.
    @Async
    public void registrar(String tipo, String userId, String email, String gymId, Integer estado, Origen origen) {
        try {
            EventoSeguridad e = new EventoSeguridad();
            e.setTipo(tipo);
            e.setDescripcion(DESCRIPCIONES.getOrDefault(tipo, tipo));
            e.setUserId(userId);
            e.setEmail(email == null ? null : recortar(email.trim().toLowerCase(), 120));
            e.setGymId(gymId);
            e.setMetodo(origen.metodo());
            e.setRuta(origen.ruta());
            e.setEstado(estado);
            e.setIp(origen.ip());
            e.setAgente(origen.agente());
            Instant ahora = Instant.now();
            e.setFecha(ahora);
            e.setExpira(ahora.plus(Duration.ofDays(EventoSeguridad.RETENCION_DIAS)));
            eventos.save(e);
        } catch (RuntimeException ex) {
            log.error("No se pudo escribir en la bitácora: {} {} usuario={} ip={}", tipo, origen.ruta(), userId, origen.ip(), ex);
        }
    }

    // Lo que ve el dueño en su panel: lo que pasó en su gimnasio y en su propia
    // cuenta. Los inicios de sesión de sus miembros no: son de ellos.
    public List<Map<String, Object>> delGimnasio(String gymId, String duenoId, int limite) {
        Query q = Query.query(new Criteria().orOperator(Criteria.where("gymId").is(gymId), Criteria.where("userId").is(duenoId)))
                .with(Sort.by(Sort.Direction.DESC, "fecha"))
                .limit(Math.max(1, Math.min(limite, 500)));
        return mongo.find(q, EventoSeguridad.class).stream().map(e -> {
            Map<String, Object> v = new LinkedHashMap<>();
            v.put("fecha", e.getFecha());
            v.put("tipo", e.getTipo());
            v.put("descripcion", e.getDescripcion());
            v.put("propio", duenoId.equals(e.getUserId()));
            v.put("email", duenoId.equals(e.getUserId()) ? e.getEmail() : null);
            v.put("metodo", e.getMetodo());
            v.put("ruta", e.getRuta());
            v.put("estado", e.getEstado());
            v.put("ip", e.getIp());
            v.put("agente", e.getAgente());
            return v;
        }).toList();
    }

    static String recortar(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }
}
