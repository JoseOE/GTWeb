package com.gymtrack.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

// Cabeceras de seguridad en cada respuesta (páginas, API y archivos). Van antes
// que cualquier otro filtro, así que también salen en los rechazos de SesionFilter.
//
// La política de contenido (CSP) solo deja cargar scripts, estilos y fuentes de
// este sitio y de los CDN que ya usan las páginas (jsDelivr, unpkg, cdnjs,
// Google Fonts y Google Analytics). Las páginas tienen scripts y estilos en
// línea, por eso lleva 'unsafe-inline'; aun así, un script inyectado no puede
// mandar datos a otro dominio (connect-src: solo el router de rutas de OSRM
// del mapa y Google Analytics) ni la página puede meterse en un
// iframe ajeno (frame-ancestors, contra clickjacking).
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CabecerasSeguridadFilter extends OncePerRequestFilter {

    static final String CSP = String.join("; ",
            "default-src 'self'",
            "script-src 'self' 'unsafe-inline' https://cdn.jsdelivr.net https://unpkg.com https://cdnjs.cloudflare.com https://www.googletagmanager.com",
            "style-src 'self' 'unsafe-inline' https://cdn.jsdelivr.net https://unpkg.com https://fonts.googleapis.com",
            "font-src 'self' data: https://fonts.gstatic.com https://unpkg.com https://cdn.jsdelivr.net",
            "img-src 'self' data: blob: https:",
            "connect-src 'self' https://router.project-osrm.org https://*.google-analytics.com https://*.analytics.google.com https://www.googletagmanager.com https://*.doubleclick.net",
            "media-src 'self' blob:",
            "frame-src 'none'",
            "frame-ancestors 'none'",
            "object-src 'none'",
            "base-uri 'self'",
            "form-action 'self'");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        response.setHeader("Content-Security-Policy", CSP);
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("X-Frame-Options", "DENY");
        response.setHeader("Referrer-Policy", "strict-origin-when-cross-origin");
        // La cámara (lector del QR de entregas) y la ubicación ("cómo llegar" del
        // inicio) solo en este mismo sitio; lo demás, en ningún lado.
        response.setHeader("Permissions-Policy", "camera=(self), geolocation=(self), microphone=(), payment=(), usb=()");
        response.setHeader("Cross-Origin-Opener-Policy", "same-origin");
        // HSTS solo por HTTPS: en Render el TLS termina en su proxy, que avisa con X-Forwarded-Proto.
        if (request.isSecure() || "https".equalsIgnoreCase(request.getHeader("X-Forwarded-Proto"))) {
            response.setHeader("Strict-Transport-Security", "max-age=31536000; includeSubDomains");
        }
        // Las respuestas de la API llevan datos personales: que no se guarden en
        // cachés compartidas. Las imágenes del catálogo sí se guardan (ImagenController).
        String uri = request.getRequestURI();
        if (uri.startsWith("/api/") && !uri.startsWith("/api/imagenes/")) {
            response.setHeader("Cache-Control", "no-store");
        }
        chain.doFilter(request, response);
    }
}
