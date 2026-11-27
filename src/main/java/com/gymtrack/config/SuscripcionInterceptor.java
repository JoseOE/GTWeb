package com.gymtrack.config;

import com.gymtrack.service.SuscripcionService;
import com.gymtrack.service.TiendaException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Solo lectura: si la suscripción del gimnasio a GymTrack venció y pasaron los
// días de gracia, el panel del dueño ya no puede hacer cambios (vender, dar
// altas, registrar pagos, editar productos...). Ver sí puede, y pagar su
// suscripción también. El lector de la puerta (/api/lectores), la tienda y la
// app de los socios no pasan por aquí: siguen funcionando.
@Component
public class SuscripcionInterceptor implements HandlerInterceptor {

    private static final Pattern RUTA = Pattern.compile("^/api/gyms/([^/]+)/(.+)$");

    private final SuscripcionService suscripciones;

    public SuscripcionInterceptor(SuscripcionService suscripciones) {
        this.suscripciones = suscripciones;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String metodo = request.getMethod();
        if ("GET".equals(metodo) || "HEAD".equals(metodo) || "OPTIONS".equals(metodo)) return true;
        Matcher m = RUTA.matcher(request.getRequestURI());
        if (!m.matches() || m.group(2).startsWith("suscripcion")) return true;
        if (suscripciones.soloLectura(m.group(1))) {
            throw new TiendaException(HttpStatus.PAYMENT_REQUIRED,
                    "Tu suscripción a GymTrack venció: el panel está en solo lectura. Renuévala en la pestaña Suscripción para volver a hacer cambios.",
                    Map.of("suscripcionVencida", true));
        }
        return true;
    }
}
