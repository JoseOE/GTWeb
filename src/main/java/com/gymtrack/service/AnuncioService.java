package com.gymtrack.service;

import com.gymtrack.model.Anuncio;
import com.gymtrack.model.Gym;
import com.gymtrack.model.User;
import com.gymtrack.repository.AnuncioRepository;
import com.gymtrack.repository.GymRepository;
import com.gymtrack.repository.UserRepository;
import com.gymtrack.util.Fechas;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

// Anuncios del gimnasio para sus miembros: el dueño los publica en el panel
// (pestaña Anuncios) y la app los muestra en Inicio mientras están vigentes.
// Si el dueño marca "Avisar a mis miembros", sale una notificación una sola vez.
@Service
public class AnuncioService {

    static final int MAX_TITULO = 60;
    static final int MAX_TEXTO = 400;
    // La app muestra los vigentes; con más de estos ya es ruido.
    static final int MAX_VIGENTES = 20;
    private static final Set<String> TIPOS = Set.of(Anuncio.AVISO, Anuncio.PROMOCION, Anuncio.CIERRE);

    private final AnuncioRepository anuncios;
    private final UserRepository users;
    private final GymRepository gyms;
    private final PushService push;

    public AnuncioService(AnuncioRepository anuncios, UserRepository users, GymRepository gyms, PushService push) {
        this.anuncios = anuncios;
        this.users = users;
        this.gyms = gyms;
        this.push = push;
    }

    // Lo que manda el panel al crear o editar.
    public static class Solicitud {
        private String titulo;
        private String texto;
        private String tipo;
        private String desde;
        private String hasta;
        private Boolean avisar;

        public String getTitulo() { return titulo; }
        public void setTitulo(String titulo) { this.titulo = titulo; }
        public String getTexto() { return texto; }
        public void setTexto(String texto) { this.texto = texto; }
        public String getTipo() { return tipo; }
        public void setTipo(String tipo) { this.tipo = tipo; }
        public String getDesde() { return desde; }
        public void setDesde(String desde) { this.desde = desde; }
        public String getHasta() { return hasta; }
        public void setHasta(String hasta) { this.hasta = hasta; }
        public Boolean getAvisar() { return avisar; }
        public void setAvisar(Boolean avisar) { this.avisar = avisar; }
    }

    // ─── Panel del dueño ───

    public List<Map<String, Object>> delGimnasio(String gymId) {
        LocalDate hoy = Fechas.hoy();
        return anuncios.findByGymIdOrderByDesdeDescCreadoEnDesc(gymId).stream().map(a -> vista(a, hoy)).toList();
    }

    // id null = nuevo. Devuelve el anuncio y, si se avisó, a cuántos miembros.
    public Map<String, Object> guardar(String gymId, String id, Solicitud s) {
        Anuncio a = id == null ? new Anuncio() : anuncios.findByIdAndGymId(id, gymId)
                .orElseThrow(() -> new TiendaException(HttpStatus.NOT_FOUND, "Ese anuncio ya no existe."));
        String titulo = limpio(s.getTitulo());
        String texto = limpio(s.getTexto());
        if (titulo.length() < 3 || titulo.length() > MAX_TITULO) {
            throw new TiendaException(HttpStatus.BAD_REQUEST, "El título debe tener de 3 a " + MAX_TITULO + " caracteres.");
        }
        if (texto.isEmpty() || texto.length() > MAX_TEXTO) {
            throw new TiendaException(HttpStatus.BAD_REQUEST, "Escribe el anuncio (hasta " + MAX_TEXTO + " caracteres).");
        }
        String tipo = limpio(s.getTipo()).toLowerCase();
        if (tipo.isEmpty()) tipo = Anuncio.AVISO;
        if (!TIPOS.contains(tipo)) throw new TiendaException(HttpStatus.BAD_REQUEST, "El tipo debe ser aviso, promoción o cierre.");
        LocalDate hoy = Fechas.hoy();
        LocalDate desde = fecha(s.getDesde(), "La fecha de inicio");
        if (desde == null) desde = hoy;
        LocalDate hasta = fecha(s.getHasta(), "La fecha de fin");
        if (hasta != null && hasta.isBefore(desde)) {
            throw new TiendaException(HttpStatus.BAD_REQUEST, "La fecha de fin no puede ser antes de la de inicio.");
        }

        Instant ahora = Instant.now();
        a.setGymId(gymId);
        a.setTitulo(titulo);
        a.setTexto(texto);
        a.setTipo(tipo);
        a.setDesde(desde);
        a.setHasta(hasta);
        if (a.getCreadoEn() == null) a.setCreadoEn(ahora);
        a.setActualizadoEn(ahora);

        boolean avisar = Boolean.TRUE.equals(s.getAvisar());
        if (avisar && !"vigente".equals(a.estado(hoy))) {
            throw new TiendaException(HttpStatus.BAD_REQUEST,
                    "Solo puedes avisar de un anuncio que ya está vigente (que empieza hoy o antes y no está pausado).");
        }
        a = anuncios.save(a);

        int avisados = 0;
        if (avisar && a.getAvisadoEn() == null) {
            avisados = avisarMiembros(a);
            a.setAvisadoEn(ahora);
            a = anuncios.save(a);
        }
        Map<String, Object> v = vista(a, hoy);
        v.put("avisados", avisados);
        return v;
    }

    public Map<String, Object> pausar(String gymId, String id, boolean pausado) {
        Anuncio a = anuncios.findByIdAndGymId(id, gymId)
                .orElseThrow(() -> new TiendaException(HttpStatus.NOT_FOUND, "Ese anuncio ya no existe."));
        a.setPausado(pausado);
        a.setActualizadoEn(Instant.now());
        return vista(anuncios.save(a), Fechas.hoy());
    }

    public void borrar(String gymId, String id) {
        anuncios.findByIdAndGymId(id, gymId).ifPresent(anuncios::delete);
    }

    // ─── App del miembro ───

    // Los vigentes de su gimnasio, si su membresía está activa.
    public List<Map<String, Object>> vigentesPara(User user) {
        if (user.getGymId() == null || !"member".equals(user.getRole()) || !user.tieneAccesoAlGimnasio()) return List.of();
        LocalDate hoy = Fechas.hoy();
        return anuncios.findByGymIdOrderByDesdeDescCreadoEnDesc(user.getGymId()).stream()
                .filter(a -> "vigente".equals(a.estado(hoy)))
                .limit(MAX_VIGENTES)
                .map(a -> vista(a, hoy))
                .toList();
    }

    // ─── Ayudantes ───

    // Push a los miembros activos con la app instalada (los que tienen token).
    private int avisarMiembros(Anuncio a) {
        String gym = gyms.findById(a.getGymId()).map(Gym::getNombre).orElse("Tu gimnasio");
        List<User> destino = users.findByGymId(a.getGymId()).stream()
                .filter(u -> "member".equals(u.getRole()) && u.tieneAccesoAlGimnasio())
                .filter(u -> u.getPushToken() != null && !u.getPushToken().isBlank())
                .toList();
        String cuerpo = a.getTexto().length() > 140 ? a.getTexto().substring(0, 137) + "…" : a.getTexto();
        push.enviarATodos(destino, gym + ": " + a.getTitulo(), cuerpo,
                Map.of("tipo", "anuncio", "anuncioId", a.getId(), "gymId", a.getGymId()));
        return destino.size();
    }

    private static Map<String, Object> vista(Anuncio a, LocalDate hoy) {
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("id", a.getId());
        v.put("titulo", a.getTitulo());
        v.put("texto", a.getTexto());
        v.put("tipo", a.getTipo());
        v.put("desde", a.getDesde() == null ? null : a.getDesde().toString());
        v.put("hasta", a.getHasta() == null ? null : a.getHasta().toString());
        v.put("estado", a.estado(hoy));
        v.put("pausado", a.isPausado());
        v.put("avisadoEn", a.getAvisadoEn());
        v.put("creadoEn", a.getCreadoEn());
        return v;
    }

    private static LocalDate fecha(String valor, String campo) {
        String v = limpio(valor);
        if (v.isEmpty()) return null;
        try {
            return LocalDate.parse(v);
        } catch (DateTimeParseException e) {
            throw new TiendaException(HttpStatus.BAD_REQUEST, campo + " no es válida (AAAA-MM-DD).");
        }
    }

    private static String limpio(String s) {
        return s == null ? "" : s.trim();
    }
}
