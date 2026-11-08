package com.gymtrack.service;

import com.gymtrack.model.Gym;
import com.gymtrack.model.Producto;
import com.gymtrack.model.SolicitudAcceso;
import com.gymtrack.model.User;
import com.gymtrack.repository.GymRepository;
import com.gymtrack.repository.ProductoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

// Renovar la membresía desde la puerta.
//
// Cuando el lector niega la entrada porque la membresía venció, al socio le
// llega un push con el plan para renovar. La app abre la pantalla de
// renovación (GET /api/tienda/renovacion), paga como cualquier compra y, al
// confirmarse el pago, ControlAccesoService.abrirTrasRenovar() aprueba la
// misma alerta de la puerta: el lector recibe "abrir" y el socio entra sin
// volver a pasar la tarjeta (mientras la alerta siga vigente, 10 minutos).
@Service
public class RenovacionAccesoService {

    private static final Logger log = LoggerFactory.getLogger(RenovacionAccesoService.class);
    // Lo que la app busca en "data" para abrir la pantalla de renovación.
    public static final String TIPO_PUSH = "renovar_membresia";

    private final PushService push;
    private final ProductoRepository productos;
    private final GymRepository gyms;

    public RenovacionAccesoService(PushService push, ProductoRepository productos, GymRepository gyms) {
        this.push = push;
        this.productos = productos;
        this.gyms = gyms;
    }

    // El plan que se le ofrece: el que tenía, si sigue a la venta; si no, el
    // destacado; si no, el de un mes; si no, el más barato. Solo planes que
    // extienden la membresía (no la inscripción).
    public Optional<Producto> planSugerido(String gymId, User socio) {
        List<Producto> planes = planesRenovables(gymId);
        if (planes.isEmpty()) return Optional.empty();
        String actual = socio.getPlanActual();
        return planes.stream().filter(p -> actual != null && actual.equalsIgnoreCase(p.getNombre())).findFirst()
                .or(() -> planes.stream().filter(p -> p.getPlan().isDestacado()).findFirst())
                .or(() -> planes.stream().filter(p -> "mes".equals(p.getPlan().getDuracionUnidad())
                        && Integer.valueOf(1).equals(p.getPlan().getDuracionCantidad())).findFirst())
                .or(() -> planes.stream().min(Comparator.comparingDouble(RenovacionAccesoService::precio)));
    }

    // Los planes publicados que extienden la membresía, del más barato al más caro.
    public List<Producto> planesRenovables(String gymId) {
        return productos.findByGymIdAndActivoTrue(gymId).stream()
                .filter(p -> p.esPlan() && p.getPlan() != null && !p.getPlan().isPagoUnico()
                        && p.getVariantes() != null && !p.getVariantes().isEmpty())
                .sorted(Comparator.comparingDouble(RenovacionAccesoService::precio))
                .toList();
    }

    // Lo que la app muestra del plan.
    public static Map<String, Object> vistaPlan(Producto p) {
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("id", p.getId());
        v.put("varianteId", p.getVariantes().get(0).getId());
        v.put("nombre", p.getNombre());
        v.put("precio", precio(p));
        Integer cantidad = p.getPlan().getDuracionCantidad();
        v.put("duracionTexto", CatalogoService.duracionTexto(
                new PlanPagado(p.getId(), p.getNombre(), p.getPlan().getDuracionUnidad(), cantidad == null ? 1 : cantidad)));
        return v;
    }

    // En segundo plano: el lector no espera a que Expo responda.
    @Async
    public void avisarVencida(User socio, SolicitudAcceso solicitud) {
        try {
            String gym = gyms.findById(solicitud.getGymId()).map(Gym::getNombre).orElse("tu gimnasio");
            Optional<Producto> plan = planSugerido(solicitud.getGymId(), socio);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("tipo", TIPO_PUSH);
            data.put("gymId", solicitud.getGymId());
            data.put("solicitudId", solicitud.getId());
            data.put("venceEn", solicitud.getCreadaEn().plus(ControlAccesoService.VIGENCIA_SOLICITUD).toString());
            String cuerpo;
            if (plan.isPresent()) {
                Producto p = plan.get();
                data.put("planId", p.getId());
                data.put("varianteId", p.getVariantes().get(0).getId());
                cuerpo = "Renueva tu " + p.getNombre() + " por " + CarritoService.dinero(precio(p))
                        + " desde la app y la puerta se abre en cuanto se confirme el pago.";
            } else {
                cuerpo = "Renuévala desde la app para volver a entrar a " + gym + ".";
            }
            boolean conToken = socio.getPushToken() != null && !socio.getPushToken().isBlank();
            log.info("Membresía vencida en la puerta de {}: aviso de renovación a {} ({}).", gym, socio.getEmail(),
                    conToken ? plan.map(Producto::getNombre).orElse("sin plan a la venta") : "sin la app instalada");
            push.enviar(socio, "Tu membresía venció", cuerpo, data);
        } catch (RuntimeException e) {
            log.warn("No se pudo avisar la renovación a {}: {}", socio.getEmail(), e.getMessage());
        }
    }

    private static double precio(Producto p) {
        return p.getVariantes().get(0).getPrecio();
    }
}
