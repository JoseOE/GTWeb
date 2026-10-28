package com.gymtrack.config;

import com.gymtrack.service.AccesoService;
import com.gymtrack.service.BitacoraService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Map;

// Anota en la bitácora (BitacoraService) las acciones del panel que cambian
// algo importante y todo acceso negado (403). Envuelve a SesionFilter, así que
// ve también lo que él rechaza; el usuario es el de la sesión que él validó.
//
// Solo cuenta lo que salió bien (2xx): un intento rechazado por una regla de
// negocio no cambió nada. Agregar una acción auditable = agregar una regla.
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class BitacoraFilter extends OncePerRequestFilter {

    private static final AntPathMatcher RUTAS = new AntPathMatcher();

    private record Regla(String metodo, String patron, String tipo) {
        boolean aplica(String m, String ruta) {
            return (metodo == null ? !"GET".equals(m) : metodo.equals(m)) && RUTAS.match(patron, ruta);
        }
    }

    // metodo null = cualquier método que cambie algo (POST, PUT, PATCH, DELETE).
    private static final List<Regla> AUDITABLES = List.of(
            new Regla("POST", "/api/cuenta/contrasena", "CONTRASENA_CAMBIADA"),
            new Regla("POST", "/api/cuenta/correo/confirmar", "CORREO_CAMBIADO"),
            new Regla("POST", "/api/gyms", "GIMNASIO_CREADO"),
            new Regla("POST", "/api/gyms/{gymId}/codigo", "CODIGO_GIMNASIO"),
            new Regla("POST", "/api/gyms/{gymId}/members", "MIEMBRO_ALTA"),
            new Regla("PATCH", "/api/gyms/{gymId}/members/*", "MIEMBRO_ESTADO"),
            new Regla("DELETE", "/api/gyms/{gymId}/members/*", "MIEMBRO_BAJA"),
            new Regla("POST", "/api/gyms/{gymId}/members/*/payments", "PAGO_REGISTRADO"),
            new Regla("POST", "/api/gyms/{gymId}/mostrador/cobrar", "VENTA_MOSTRADOR"),
            new Regla("POST", "/api/gyms/{gymId}/ventas/pedidos/*/reembolsar", "PEDIDO_REEMBOLSADO"),
            new Regla("POST", "/api/gyms/{gymId}/ventas/pedidos/*/cancelar", "PEDIDO_CANCELADO"),
            new Regla("POST", "/api/gyms/{gymId}/ventas/pedidos/*/entregar", "PEDIDO_ENTREGADO"),
            new Regla(null, "/api/gyms/{gymId}/productos/**", "PRODUCTO_CAMBIADO"),
            new Regla(null, "/api/gyms/{gymId}/planes/**", "PLAN_CAMBIADO"));

    private final BitacoraService bitacora;

    public BitacoraFilter(BitacoraService bitacora) {
        this.bitacora = bitacora;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return "OPTIONS".equals(request.getMethod()) || !request.getRequestURI().startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        try {
            chain.doFilter(request, response);
        } finally {
            anotar(request, response.getStatus());
        }
    }

    private void anotar(HttpServletRequest request, int estado) {
        String ruta = request.getRequestURI();
        String tipo = null;
        // El login anota lo suyo (su 403 es "verifica tu correo", no un acceso negado).
        if (ruta.equals("/api/users/login")) return;
        if (estado == HttpServletResponse.SC_FORBIDDEN) {
            tipo = BitacoraService.ACCESO_DENEGADO;
        } else if (estado >= 200 && estado < 300) {
            String metodo = request.getMethod();
            tipo = AUDITABLES.stream().filter(r -> r.aplica(metodo, ruta)).map(Regla::tipo).findFirst().orElse(null);
        }
        if (tipo == null) return;
        // El de la sesión si la hubo (SesionFilter lo deja en el atributo); si no, el
        // X-User-Id de la transición, que SesionFilter ya revisó contra el recurso.
        Object sesion = request.getAttribute(SesionFilter.ATRIBUTO_USUARIO);
        String userId = sesion != null ? sesion.toString() : request.getHeader(AccesoService.ENCABEZADO);
        bitacora.registrar(tipo, userId, null, gymDe(ruta), estado, BitacoraService.Origen.de(request));
    }

    private static String gymDe(String ruta) {
        if (!RUTAS.match("/api/gyms/{gymId}/**", ruta)) return null;
        Map<String, String> v = RUTAS.extractUriTemplateVariables("/api/gyms/{gymId}/**", ruta);
        String gymId = v.get("gymId");
        return gymId == null || gymId.equals("lookup") || gymId.equals("directory") ? null : gymId;
    }
}
