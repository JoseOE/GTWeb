package com.gymtrack.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gymtrack.repository.GymRepository;
import com.gymtrack.repository.MachineRepository;
import com.gymtrack.repository.RoutineRepository;
import com.gymtrack.repository.UserRepository;
import com.gymtrack.service.AccesoService;
import com.gymtrack.service.SesionService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

// Quién llama a la API y qué puede tocar.
//
// La identidad sale del token de sesión (Authorization: Bearer <token>) que
// entregan el login y la verificación del correo. Con token, el encabezado
// X-User-Id que ya usan los controladores se rellena aquí con el usuario de la
// sesión (y si la página manda otro, se rechaza), así que ningún controlador
// puede recibir una identidad inventada.
//
// Transición (EXIGIR_TOKEN=false, mientras la app móvil se actualiza):
//  - Las rutas del panel (las usa solo la página) exigen token desde ya.
//  - Las que comparte con la app aceptan todavía el X-User-Id viejo, o nada.
// Con EXIGIR_TOKEN=true, todo lo que no es público exige token.
//
// Además de la sesión, aquí se revisa a quién le pertenece cada recurso: el
// gimnasio a su dueño y cada cuenta a su usuario. La tienda, los recibos y los
// simuladores lo revisan en sus propios servicios (AccesoService).
@Component
public class SesionFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(SesionFilter.class);
    private static final AntPathMatcher RUTAS = new AntPathMatcher();
    private static final String NO_ES_TUYO = "No tienes permiso para ver o cambiar esto.";

    private record Regla(String metodo, String patron) {
        boolean aplica(String m, String ruta) {
            return (metodo == null || metodo.equals(m)) && RUTAS.match(patron, ruta);
        }
    }

    private static Regla regla(String metodo, String patron) {
        return new Regla(metodo, patron);
    }

    // Sin sesión: crear cuenta, entrar, verificar, recuperar, el catálogo, la
    // tienda que despierta, el webhook firmado de Medusa, las imágenes y el
    // simulador de PayPal (su id de orden es la llave).
    private static final List<Regla> PUBLICAS = List.of(
            regla("POST", "/api/users/register"),
            regla("POST", "/api/users/login"),
            regla("POST", "/api/users/logout"),
            regla("POST", "/api/cuenta/verificar"),
            regla("POST", "/api/cuenta/verificar/reenviar"),
            regla("POST", "/api/cuenta/recuperar"),
            regla("POST", "/api/cuenta/restablecer"),
            regla("GET", "/api/gyms/lookup"),
            regla("GET", "/api/gyms/directory"),
            regla("GET", "/api/servicios"),
            regla("GET", "/api/servicios/*"),
            regla("GET", "/api/tienda/estado"),
            regla("GET", "/api/tienda/categorias"),
            regla("POST", "/api/tienda/webhooks/medusa"),
            regla("GET", "/api/imagenes/*"),
            regla("GET", "/api/simuladores/paypal/ordenes/*"),
            regla("POST", "/api/simuladores/paypal/ordenes/*/aprobar"),
            regla("POST", "/api/simuladores/paypal/ordenes/*/cancelar"));

    // Solo las usa el panel de la página: exigen sesión aunque la app siga en transición.
    private static final List<Regla> PANEL = List.of(
            regla("POST", "/api/gyms"),
            regla("POST", "/api/gyms/*/codigo"),
            regla("GET", "/api/gyms/*/members"),
            regla("POST", "/api/gyms/*/members"),
            regla(null, "/api/gyms/*/members/*"),
            regla("POST", "/api/gyms/*/members/*/payments"),
            regla("POST", "/api/gyms/*/machines"),
            regla("POST", "/api/gyms/*/machines/import"),
            regla(null, "/api/machines/*"),
            regla("POST", "/api/gyms/*/routines"),
            regla(null, "/api/routines/*"),
            regla(null, "/api/gyms/*/productos/**"),
            regla(null, "/api/gyms/*/productos"),
            regla(null, "/api/gyms/*/planes/**"),
            regla(null, "/api/gyms/*/planes"),
            regla(null, "/api/gyms/*/imagenes"),
            regla(null, "/api/gyms/*/mostrador/**"),
            regla(null, "/api/gyms/*/ventas/**"),
            regla(null, "/api/simuladores/paynet/**"));

    public static final String ATRIBUTO_USUARIO = "gymtrack.sesion.usuario";

    private final SesionService sesiones;
    private final GymRepository gyms;
    private final UserRepository users;
    private final MachineRepository machines;
    private final RoutineRepository routines;
    private final ObjectMapper json;
    private final boolean exigirToken;

    public SesionFilter(SesionService sesiones, GymRepository gyms, UserRepository users,
                        MachineRepository machines, RoutineRepository routines, ObjectMapper json,
                        @Value("${seguridad.exigir-token:false}") boolean exigirToken) {
        this.sesiones = sesiones;
        this.gyms = gyms;
        this.users = users;
        this.machines = machines;
        this.routines = routines;
        this.json = json;
        this.exigirToken = exigirToken;
        if (!exigirToken) {
            log.warn("Seguridad en transición (EXIGIR_TOKEN=false): las rutas que comparte la app aceptan X-User-Id sin token. "
                    + "Actívalo en cuanto la app mande el token.");
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/") || "OPTIONS".equals(request.getMethod());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String metodo = request.getMethod();
        String ruta = request.getRequestURI();
        boolean publica = PUBLICAS.stream().anyMatch(r -> r.aplica(metodo, ruta));

        String token = token(request);
        String usuario = token == null ? null : sesiones.usuarioDe(token).orElse(null);
        String encabezado = request.getHeader(AccesoService.ENCABEZADO);

        if (publica) {
            // Un token viejo no estorba en lo público: simplemente se ignora.
            chain.doFilter(usuario == null ? request : conUsuario(request, usuario), response);
            return;
        }
        if (token != null && usuario == null) {
            responder(response, HttpServletResponse.SC_UNAUTHORIZED, "Tu sesión venció. Inicia sesión de nuevo.", true);
            return;
        }
        if (usuario != null && encabezado != null && !encabezado.isBlank() && !encabezado.equals(usuario)) {
            responder(response, HttpServletResponse.SC_FORBIDDEN, "La sesión no corresponde a ese usuario.", false);
            return;
        }
        if (usuario == null) {
            if (exigirToken || PANEL.stream().anyMatch(r -> r.aplica(metodo, ruta))) {
                responder(response, HttpServletResponse.SC_UNAUTHORIZED, "Inicia sesión para continuar.", true);
                return;
            }
            // Transición: la identidad (si la hay) es el encabezado de siempre.
            usuario = encabezado == null || encabezado.isBlank() ? null : encabezado;
        }

        if (usuario != null) {
            String problema = autorizar(metodo, ruta, usuario);
            if (problema != null) {
                responder(response, HttpServletResponse.SC_FORBIDDEN, problema, false);
                return;
            }
        }
        chain.doFilter(token != null ? conUsuario(request, usuario) : request, response);
    }

    // A quién le pertenece cada recurso. null = adelante; si no, el motivo.
    private String autorizar(String metodo, String ruta, String usuario) {
        Map<String, String> v;
        if ((v = extraer("/api/users/{userId}/**", ruta)) != null) {
            return usuario.equals(v.get("userId")) ? null : "Solo puedes ver o cambiar tu propia cuenta.";
        }
        if ("GET".equals(metodo) && (v = extraer("/api/gyms/{gymId}/members/{miembroId}/payments", ruta)) != null) {
            return esDueno(v.get("gymId"), usuario) || usuario.equals(v.get("miembroId")) ? null : NO_ES_TUYO;
        }
        if ("GET".equals(metodo) && ((v = extraer("/api/gyms/{gymId}/machines", ruta)) != null
                || (v = extraer("/api/gyms/{gymId}/routines", ruta)) != null)) {
            return esDueno(v.get("gymId"), usuario) || esMiembro(v.get("gymId"), usuario) ? null : NO_ES_TUYO;
        }
        if ("GET".equals(metodo) && extraer("/api/gyms/{gymId}", ruta) != null) {
            // Cualquiera ve los datos públicos; el controlador solo le da el código al dueño.
            return null;
        }
        if ((v = extraer("/api/gyms/{gymId}/**", ruta)) != null) {
            return esDueno(v.get("gymId"), usuario) ? null : "No administras ese gimnasio.";
        }
        if ((v = extraer("/api/machines/{id}", ruta)) != null) {
            String gymId = machines.findById(v.get("id")).map(m -> m.getGymId()).orElse(null);
            return gymId == null || esDueno(gymId, usuario) ? null : "No administras ese gimnasio.";
        }
        if ((v = extraer("/api/routines/{id}", ruta)) != null) {
            String gymId = routines.findById(v.get("id")).map(r -> r.getGymId()).orElse(null);
            return gymId == null || esDueno(gymId, usuario) ? null : "No administras ese gimnasio.";
        }
        return null;
    }

    private boolean esDueno(String gymId, String usuario) {
        return gyms.findById(gymId).map(g -> usuario.equals(g.getOwnerId())).orElse(false);
    }

    private boolean esMiembro(String gymId, String usuario) {
        return users.findById(usuario).map(u -> gymId.equals(u.getGymId())).orElse(false);
    }

    private static Map<String, String> extraer(String patron, String ruta) {
        return RUTAS.match(patron, ruta) ? RUTAS.extractUriTemplateVariables(patron, ruta) : null;
    }

    private static String token(HttpServletRequest request) {
        String auth = request.getHeader("Authorization");
        if (auth == null || !auth.regionMatches(true, 0, "Bearer ", 0, 7)) return null;
        String token = auth.substring(7).trim();
        return token.isEmpty() ? null : token;
    }

    private void responder(HttpServletResponse response, int status, String error, boolean sesionVencida) throws IOException {
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("error", error);
        if (sesionVencida) cuerpo.put("sesionVencida", true);
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(json.writeValueAsString(cuerpo));
    }

    private static HttpServletRequest conUsuario(HttpServletRequest request, String usuario) {
        request.setAttribute(ATRIBUTO_USUARIO, usuario);
        return new ConUsuario(request, usuario);
    }

    // La petición con X-User-Id = usuario de la sesión, para los controladores de siempre.
    private static final class ConUsuario extends HttpServletRequestWrapper {
        private final String usuario;

        ConUsuario(HttpServletRequest request, String usuario) {
            super(request);
            this.usuario = usuario;
        }

        @Override
        public String getHeader(String nombre) {
            return AccesoService.ENCABEZADO.equalsIgnoreCase(nombre) ? usuario : super.getHeader(nombre);
        }

        @Override
        public Enumeration<String> getHeaders(String nombre) {
            return AccesoService.ENCABEZADO.equalsIgnoreCase(nombre)
                    ? Collections.enumeration(List.of(usuario))
                    : super.getHeaders(nombre);
        }

        @Override
        public Enumeration<String> getHeaderNames() {
            Set<String> nombres = new LinkedHashSet<>(Collections.list(super.getHeaderNames()));
            nombres.add(AccesoService.ENCABEZADO);
            return Collections.enumeration(nombres);
        }
    }
}
