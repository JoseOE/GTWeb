package com.gymtrack.service;

import com.gymtrack.model.Gym;
import com.gymtrack.model.Opinion;
import com.gymtrack.model.User;
import com.gymtrack.repository.GymRepository;
import com.gymtrack.repository.OpinionRepository;
import com.gymtrack.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

// Opiniones sobre GymTrack de dueños (desde el panel) y miembros (desde la app
// o Mi cuenta). Cada cuenta tiene una sola opinión, que puede cambiar o borrar.
//
// El carrusel de la página de inicio solo muestra las de MINIMO_DESTACADA
// estrellas o más (el criterio de "más de 3.8"), con el nombre corto de quien
// opinó y la foto de su gimnasio. Nunca su correo.
@Service
public class OpinionService {

    public static final int MINIMO_DESTACADA = 4;
    static final int MIN_TEXTO = 10;
    static final int MAX_TEXTO = 300;

    private final OpinionRepository opiniones;
    private final UserRepository users;
    private final GymRepository gyms;

    public OpinionService(OpinionRepository opiniones, UserRepository users, GymRepository gyms) {
        this.opiniones = opiniones;
        this.users = users;
        this.gyms = gyms;
    }

    public Optional<Map<String, Object>> mia(User user) {
        return opiniones.findByUserId(user.getId()).map(o -> vista(o, user, gyms.findById(o.getGymId()).orElse(null)));
    }

    // Crea o cambia la opinión de quien llama. Hay que tener gimnasio: el dueño
    // con el suyo configurado y el miembro ya aceptado en uno.
    public Map<String, Object> guardar(User user, Integer estrellas, String texto) {
        if (user.getGymId() == null || user.getGymId().isBlank()) {
            throw new TiendaException(HttpStatus.BAD_REQUEST, "owner".equals(user.getRole())
                    ? "Configura primero tu gimnasio para dejar tu opinión."
                    : "Únete a un gimnasio para dejar tu opinión.");
        }
        if (!user.tieneAccesoAlGimnasio()) {
            throw new TiendaException(HttpStatus.BAD_REQUEST, "Tu gimnasio todavía no aprueba tu solicitud.");
        }
        Gym gym = gyms.findById(user.getGymId())
                .orElseThrow(() -> new TiendaException(HttpStatus.BAD_REQUEST, "Tu gimnasio ya no existe."));
        if (estrellas == null || estrellas < 1 || estrellas > 5) {
            throw new TiendaException(HttpStatus.BAD_REQUEST, "Califica de 1 a 5 estrellas.");
        }
        String limpio = texto == null ? "" : texto.trim().replaceAll("\\s+", " ");
        if (limpio.length() < MIN_TEXTO || limpio.length() > MAX_TEXTO) {
            throw new TiendaException(HttpStatus.BAD_REQUEST, "Escribe tu opinión en " + MIN_TEXTO + " a " + MAX_TEXTO + " caracteres.");
        }
        Instant ahora = Instant.now();
        Opinion o = opiniones.findByUserId(user.getId()).orElseGet(() -> {
            Opinion nueva = new Opinion();
            nueva.setUserId(user.getId());
            nueva.setCreadaEn(ahora);
            return nueva;
        });
        o.setGymId(gym.getId());
        o.setRol(user.getRole());
        o.setEstrellas(estrellas);
        o.setTexto(limpio);
        o.setActualizadaEn(ahora);
        return vista(opiniones.save(o), user, gym);
    }

    public void borrar(User user) {
        opiniones.deleteByUserId(user.getId());
    }

    // Para el carrusel: las más recientes de 4★ o más, una por gimnasio primero
    // (para que no las acapare uno solo) y luego las demás.
    public List<Map<String, Object>> destacadas(int limite) {
        List<Opinion> candidatas = opiniones.findTop40ByEstrellasGreaterThanEqualOrderByActualizadaEnDesc(MINIMO_DESTACADA);
        List<Opinion> primero = new ArrayList<>();
        List<Opinion> despues = new ArrayList<>();
        Set<String> vistos = new HashSet<>();
        for (Opinion o : candidatas) (vistos.add(o.getGymId()) ? primero : despues).add(o);
        primero.addAll(despues);
        List<Map<String, Object>> lista = new ArrayList<>();
        for (Opinion o : primero) {
            if (lista.size() >= Math.max(1, Math.min(limite, 24))) break;
            Optional<User> autor = users.findById(o.getUserId());
            Optional<Gym> gym = gyms.findById(o.getGymId());
            if (autor.isEmpty() || gym.isEmpty()) continue;
            Map<String, Object> v = vista(o, autor.get(), gym.get());
            v.remove("id");
            lista.add(v);
        }
        return lista;
    }

    private static Map<String, Object> vista(Opinion o, User autor, Gym gym) {
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("id", o.getId());
        v.put("autor", nombreCorto(autor.getNombre()));
        v.put("rol", "owner".equals(o.getRol()) ? "Dueño" : "Miembro");
        v.put("estrellas", o.getEstrellas());
        v.put("texto", o.getTexto());
        v.put("fecha", o.getActualizadaEn());
        Map<String, Object> g = new LinkedHashMap<>();
        g.put("nombre", gym == null ? "" : gym.getNombre());
        g.put("foto", gym == null ? null : gym.getFoto());
        g.put("logo", gym == null ? null : gym.getLogo());
        g.put("colorPrimario", gym == null ? null : gym.getColorPrimario());
        v.put("gimnasio", g);
        return v;
    }

    // "Mariana López Pérez" → "Mariana L."
    static String nombreCorto(String nombre) {
        if (nombre == null || nombre.isBlank()) return "Usuario de GymTrack";
        String[] partes = nombre.trim().split("\\s+");
        return partes.length == 1 ? partes[0] : partes[0] + " " + partes[1].charAt(0) + ".";
    }
}
