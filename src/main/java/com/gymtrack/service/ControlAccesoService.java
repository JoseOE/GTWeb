package com.gymtrack.service;

import com.gymtrack.model.Acceso;
import com.gymtrack.model.FotoAcceso;
import com.gymtrack.model.Lector;
import com.gymtrack.model.SolicitudAcceso;
import com.gymtrack.model.Tarjeta;
import com.gymtrack.model.User;
import com.gymtrack.repository.AccesoRepository;
import com.gymtrack.repository.FotoAccesoRepository;
import com.gymtrack.repository.LectorRepository;
import com.gymtrack.repository.SolicitudAccesoRepository;
import com.gymtrack.repository.TarjetaRepository;
import com.gymtrack.repository.UserRepository;
import com.gymtrack.util.Fechas;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

// Control de acceso con el lector RFID de la puerta.
//
// El lector (LILYGO) manda cada tag que lee y aquí se decide, con las mismas
// reglas que se probaron sin red en la etapa 2:
//   - socio fuera con la membresía al día  -> ENTRADA
//   - socio dentro                         -> SALIDA (salir no se valida)
//   - membresía vencida o tag desconocido  -> NEGADO, y queda una solicitud
//     para que el dueño vea las fotos de la XIAO y decida si abre.
// La decisión del dueño le llega al lector por /api/lectores/comandos, que el
// lector consulta todo el tiempo (long polling): así no hace falta MQTT.
@Service
public class ControlAccesoService {

    public static final String LLAVE = "X-Lector-Key";

    // Entre un pase y el siguiente del mismo socio (en la puerta real, 2 min).
    static final Duration MINIMO_ENTRE_PASES = Duration.ofSeconds(10);
    // Quien entró y no pasó el tag al salir cuenta como fuera después de esto.
    static final Duration SALIDA_AUTOMATICA = Duration.ofHours(4);
    // Un mismo tag negado no abre otra solicitud mientras haya una así de reciente.
    static final Duration SOLICITUD_REPETIDA = Duration.ofMinutes(2);
    // El dueño puede abrir hasta este tiempo después del intento.
    static final Duration VIGENCIA_SOLICITUD = Duration.ofMinutes(10);
    // Las fotos que llegan en este lapso son de la última solicitud del lector.
    static final Duration FOTOS_DE_LA_SOLICITUD = Duration.ofSeconds(60);
    static final int FOTOS_POR_ALERTA = 3;
    // El modo registro espera el tag este tiempo; el tag capturado se puede asignar durante 10 min.
    static final Duration MODO_REGISTRO = Duration.ofSeconds(60);
    static final Duration TAG_CAPTURADO = Duration.ofMinutes(10);
    static final Duration EN_LINEA = Duration.ofSeconds(60);
    static final int MAX_FOTO = 1_500_000;

    private static final Pattern EPC = Pattern.compile("^[0-9A-F]{8,64}$");
    private static final SecureRandom AZAR = new SecureRandom();

    private final LectorRepository lectores;
    private final TarjetaRepository tarjetas;
    private final AccesoRepository accesos;
    private final SolicitudAccesoRepository solicitudes;
    private final FotoAccesoRepository fotos;
    private final UserRepository users;
    private final MongoTemplate mongo;

    public ControlAccesoService(LectorRepository lectores, TarjetaRepository tarjetas, AccesoRepository accesos,
                                SolicitudAccesoRepository solicitudes, FotoAccesoRepository fotos, UserRepository users,
                                MongoTemplate mongo) {
        this.lectores = lectores;
        this.tarjetas = tarjetas;
        this.accesos = accesos;
        this.solicitudes = solicitudes;
        this.fotos = fotos;
        this.users = users;
        this.mongo = mongo;
    }

    // ─────────────────────────── Lado del lector ───────────────────────────

    public Lector lectorDe(String llave) {
        if (llave == null || llave.isBlank()) {
            throw new TiendaException(HttpStatus.UNAUTHORIZED, "Falta la llave del lector.");
        }
        Lector lector = lectores.findByLlaveHash(hash(llave.trim()))
                .orElseThrow(() -> new TiendaException(HttpStatus.UNAUTHORIZED, "Llave de lector no válida."));
        // Solo ese campo: guardar el documento entero pisaría el modo registro
        // que el panel pudo activar mientras el lector esperaba comandos.
        lector.setUltimaConexion(Instant.now());
        mongo.updateFirst(Query.query(Criteria.where("_id").is(lector.getId())),
                Update.update("ultimaConexion", lector.getUltimaConexion()), Lector.class);
        return lector;
    }

    // La XIAO reconoció un comando de voz con su modelo (Edge Impulse) mientras
    // se presionaba su botón: "entrada" prende su LED y "salida" lo apaga.
    public Map<String, Object> registrarVoz(Lector lector, String comandoCrudo, Double confianza) {
        String comando = normalizar(comandoCrudo);
        if (!comando.equals("entrada") && !comando.equals("salida")) {
            throw new TiendaException(HttpStatus.BAD_REQUEST, "El comando de voz debe ser \"entrada\" o \"salida\".");
        }
        String led = comando.equals("entrada") ? "encendido" : "apagado";
        String porcentaje = confianza == null ? "" : " (" + Math.round(confianza * 100) + " %)";
        guardarAcceso(lector, null, null, "Comando de voz", Acceso.VOZ,
                "\"" + comando + "\"" + porcentaje + ": LED " + led, null);
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("resultado", "OK");
        r.put("comando", comando);
        r.put("led", led);
        return r;
    }

    // "Entrada " → "entrada": para comparar lo que manda el modelo.
    static String normalizar(String texto) {
        if (texto == null) return "";
        return java.text.Normalizer.normalize(texto.trim(), java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").toLowerCase();
    }

    public Map<String, Object> registrarLectura(Lector lector, String epcCrudo) {
        String epc = normalizarEpc(epcCrudo);
        Instant ahora = Instant.now();

        if (lector.getRegistroHasta() != null && ahora.isBefore(lector.getRegistroHasta())) {
            lector.setRegistroHasta(null);
            lector.setEpcCapturado(epc);
            lector.setEpcCapturadoEn(ahora);
            lectores.save(lector);
            String duenoActual = tarjetas.findByEpc(epc).flatMap(t -> users.findById(t.getUserId())).map(ControlAccesoService::nombreDe).orElse("");
            return respuesta("REGISTRO", duenoActual, "Tag capturado: asígnalo en el panel", null);
        }

        Tarjeta tarjeta = tarjetas.findByEpc(epc).filter(t -> lector.getGymId().equals(t.getGymId())).orElse(null);
        if (tarjeta == null) {
            return negar(lector, epc, null, "Tag desconocido");
        }
        User socio = users.findById(tarjeta.getUserId()).filter(u -> lector.getGymId().equals(u.getGymId())).orElse(null);
        if (socio == null) {
            return negar(lector, epc, null, "El tag es de alguien que ya no es socio");
        }

        Optional<Acceso> ultimo = accesos.findFirstByUserIdAndTipoInOrderByFechaDesc(socio.getId(), List.of(Acceso.ENTRADA, Acceso.SALIDA));
        if (ultimo.isPresent() && Duration.between(ultimo.get().getFecha(), ahora).compareTo(MINIMO_ENTRE_PASES) < 0) {
            return respuesta("IGNORADO", socio.getNombre(), "Muy pronto desde su último pase", null);
        }
        if (estaDentro(ultimo, ahora)) {
            guardarAcceso(lector, epc, socio.getId(), socio.getNombre(), Acceso.SALIDA, null, null);
            return conAforo(respuesta(Acceso.SALIDA, socio.getNombre(), null, null), lector.getGymId());
        }
        if (!membresiaVigente(socio)) {
            return negar(lector, epc, socio, "Membresía vencida");
        }
        guardarAcceso(lector, epc, socio.getId(), socio.getNombre(), Acceso.ENTRADA, null, null);
        return conAforo(respuesta(Acceso.ENTRADA, socio.getNombre(), null, null), lector.getGymId());
    }

    // "dentro": cuántos socios con tag quedan en el gimnasio. El lector se lo
    // pasa a la ESP32-C6 del foco, que lo deja prendido mientras haya alguien.
    private Map<String, Object> conAforo(Map<String, Object> r, String gymId) {
        Instant ahora = Instant.now();
        long dentro = tarjetas.findByGymId(gymId).stream().map(Tarjeta::getUserId).distinct()
                .filter(id -> estaDentro(accesos.findFirstByUserIdAndTipoInOrderByFechaDesc(id, List.of(Acceso.ENTRADA, Acceso.SALIDA)), ahora))
                .count();
        r.put("dentro", dentro);
        return r;
    }

    // Solo una alerta nueva pide fotos ("fotos": true): si el mismo tag vuelve a
    // pasar mientras su alerta sigue pendiente, la XIAO no toma otras 3.
    private Map<String, Object> negar(Lector lector, String epc, User socio, String motivo) {
        String nombre = socio == null ? null : socio.getNombre();
        Instant ahora = Instant.now();
        // El mismo tag frente al lector: no se llena el panel de alertas repetidas.
        Optional<SolicitudAcceso> reciente = solicitudes.findFirstByLectorIdAndEpcAndEstadoAndCreadaEnAfter(
                lector.getId(), epc, SolicitudAcceso.PENDIENTE, ahora.minus(SOLICITUD_REPETIDA));
        if (reciente.isPresent()) {
            return respuesta(Acceso.NEGADO, nombre, motivo, reciente.get().getId());
        }
        SolicitudAcceso s = new SolicitudAcceso();
        s.setGymId(lector.getGymId());
        s.setLectorId(lector.getId());
        s.setEpc(epc);
        s.setUserId(socio == null ? null : socio.getId());
        s.setNombre(nombre);
        s.setMotivo(motivo);
        s.setCreadaEn(ahora);
        s = solicitudes.save(s);
        guardarAcceso(lector, epc, s.getUserId(), nombre, Acceso.NEGADO, motivo, s.getId());
        Map<String, Object> r = respuesta(Acceso.NEGADO, nombre, motivo, s.getId());
        r.put("fotos", true);
        return r;
    }

    // Espera hasta "espera" a que el dueño decida algo para este lector.
    public Optional<Map<String, Object>> esperarComando(Lector lector, Duration espera) {
        Instant limite = Instant.now().plus(espera);
        while (true) {
            Optional<SolicitudAcceso> decidida = solicitudes.findFirstByLectorIdAndEstadoInAndComandoEntregadoFalseAndAtendidaEnAfter(
                    lector.getId(), List.of(SolicitudAcceso.APROBADA, SolicitudAcceso.RECHAZADA), Instant.now().minus(VIGENCIA_SOLICITUD));
            if (decidida.isPresent()) {
                SolicitudAcceso s = decidida.get();
                s.setComandoEntregado(true);
                solicitudes.save(s);
                Map<String, Object> comando = new LinkedHashMap<>();
                comando.put("comando", SolicitudAcceso.APROBADA.equals(s.getEstado()) ? "abrir" : "negar");
                comando.put("solicitudId", s.getId());
                comando.put("nombre", s.getNombre() == null ? "Visitante" : s.getNombre());
                return Optional.of(comando);
            }
            if (Instant.now().isAfter(limite)) return Optional.empty();
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return Optional.empty();
            }
        }
    }

    public Map<String, Object> guardarFoto(Lector lector, byte[] datos) {
        if (datos == null || datos.length < 4 || (datos[0] & 0xFF) != 0xFF || (datos[1] & 0xFF) != 0xD8) {
            throw new TiendaException(HttpStatus.BAD_REQUEST, "La foto debe ser un JPEG.");
        }
        if (datos.length > MAX_FOTO) {
            throw new TiendaException(HttpStatus.BAD_REQUEST, "La foto es demasiado pesada (máximo 1.5 MB).");
        }
        Optional<SolicitudAcceso> solicitud = solicitudes.findFirstByLectorIdAndCreadaEnAfterOrderByCreadaEnDesc(
                lector.getId(), Instant.now().minus(FOTOS_DE_LA_SOLICITUD));
        if (solicitud.isPresent() && solicitud.get().getFotos().size() >= FOTOS_POR_ALERTA) {
            throw new TiendaException(HttpStatus.CONFLICT, "Esa alerta ya tiene sus " + FOTOS_POR_ALERTA + " fotos.");
        }

        FotoAcceso foto = new FotoAcceso();
        foto.setGymId(lector.getGymId());
        foto.setLectorId(lector.getId());
        foto.setSolicitudId(solicitud.map(SolicitudAcceso::getId).orElse(null));
        foto.setDatos(datos);
        foto.setCreadaEn(Instant.now());
        String fotoId = fotos.save(foto).getId();

        solicitud.ifPresent(s -> {
            s.getFotos().add(fotoId);
            solicitudes.save(s);
        });
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("id", fotoId);
        r.put("solicitudId", foto.getSolicitudId());
        return r;
    }

    // Para decidir sin internet: cada tag del gimnasio y si hoy puede entrar.
    public List<Map<String, Object>> lista(Lector lector) {
        Map<String, User> socios = socios(lector.getGymId());
        List<Map<String, Object>> lista = new ArrayList<>();
        for (Tarjeta t : tarjetas.findByGymId(lector.getGymId())) {
            User u = socios.get(t.getUserId());
            if (u == null) continue;
            lista.add(Map.of("epc", t.getEpc(), "nombre", nombreDe(u), "permitido", membresiaVigente(u)));
        }
        return lista;
    }

    // ─────────────────────────── Lado del panel ───────────────────────────

    public Map<String, Object> resumen(String gymId) {
        Instant ahora = Instant.now();
        Map<String, User> socios = socios(gymId);

        List<Map<String, Object>> listaLectores = lectores.findByGymId(gymId).stream().map(l -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", l.getId());
            m.put("nombre", l.getNombre());
            m.put("ultimaConexion", l.getUltimaConexion());
            m.put("enLinea", l.getUltimaConexion() != null && Duration.between(l.getUltimaConexion(), ahora).compareTo(EN_LINEA) < 0);
            boolean esperando = l.getRegistroHasta() != null && ahora.isBefore(l.getRegistroHasta());
            m.put("registroHasta", esperando ? l.getRegistroHasta() : null);
            boolean capturado = l.getEpcCapturado() != null && l.getEpcCapturadoEn() != null
                    && Duration.between(l.getEpcCapturadoEn(), ahora).compareTo(TAG_CAPTURADO) < 0;
            m.put("epcCapturado", capturado ? l.getEpcCapturado() : null);
            m.put("epcCapturadoDe", capturado ? tarjetas.findByEpc(l.getEpcCapturado())
                    .filter(t -> gymId.equals(t.getGymId()))
                    .map(t -> socios.containsKey(t.getUserId()) ? nombreDe(socios.get(t.getUserId())) : null)
                    .orElse(null) : null);
            return m;
        }).toList();

        List<Map<String, Object>> listaTarjetas = new ArrayList<>();
        List<String> dentro = new ArrayList<>();
        for (Tarjeta t : tarjetas.findByGymId(gymId)) {
            User u = socios.get(t.getUserId());
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", t.getId());
            m.put("epc", t.getEpc());
            m.put("userId", t.getUserId());
            m.put("nombre", u == null ? "(ya no es socio)" : nombreDe(u));
            m.put("membresiaVigente", u != null && membresiaVigente(u));
            boolean estaDentro = u != null && estaDentro(
                    accesos.findFirstByUserIdAndTipoInOrderByFechaDesc(u.getId(), List.of(Acceso.ENTRADA, Acceso.SALIDA)), ahora);
            m.put("dentro", estaDentro);
            if (estaDentro && !dentro.contains(nombreDe(u))) dentro.add(nombreDe(u));
            listaTarjetas.add(m);
        }

        List<Map<String, Object>> listaSocios = socios.values().stream()
                .map(u -> Map.<String, Object>of("id", u.getId(), "nombre", nombreDe(u)))
                .sorted((a, b) -> ((String) a.get("nombre")).compareToIgnoreCase((String) b.get("nombre")))
                .toList();

        List<Map<String, Object>> listaSolicitudes = solicitudes.findTop20ByGymIdOrderByCreadaEnDesc(gymId).stream().map(s -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", s.getId());
            m.put("epc", s.getEpc());
            m.put("nombre", s.getNombre());
            m.put("motivo", s.getMotivo());
            m.put("estado", estadoDe(s, ahora));
            m.put("creadaEn", s.getCreadaEn());
            m.put("atendidaEn", s.getAtendidaEn());
            m.put("fotos", s.getFotos());
            return m;
        }).toList();

        List<Map<String, Object>> listaAccesos = accesos.findTop40ByGymIdOrderByFechaDesc(gymId).stream().map(a -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("fecha", a.getFecha());
            m.put("nombre", a.getNombre());
            m.put("epc", a.getEpc());
            m.put("tipo", a.getTipo());
            m.put("motivo", a.getMotivo());
            return m;
        }).toList();

        Map<String, Object> r = new LinkedHashMap<>();
        r.put("lectores", listaLectores);
        r.put("tarjetas", listaTarjetas);
        r.put("socios", listaSocios);
        r.put("solicitudes", listaSolicitudes);
        r.put("accesos", listaAccesos);
        r.put("dentro", dentro);
        // El último comando de voz dice cómo quedó el LED de la XIAO.
        r.put("voz", accesos.findFirstByGymIdAndTipoOrderByFechaDesc(gymId, Acceso.VOZ).map(a -> {
            Map<String, Object> v = new LinkedHashMap<>();
            v.put("encendido", a.getMotivo() != null && a.getMotivo().startsWith("\"entrada\""));
            v.put("detalle", a.getMotivo());
            v.put("fecha", a.getFecha());
            return v;
        }).orElse(null));
        return r;
    }

    // La llave solo se devuelve aquí; después ya no se puede ver.
    public Map<String, Object> crearLector(String gymId, String nombre) {
        byte[] bytes = new byte[24];
        AZAR.nextBytes(bytes);
        String llave = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        Lector l = new Lector();
        l.setGymId(gymId);
        l.setNombre(nombre == null || nombre.isBlank() ? "Puerta principal" : nombre.trim());
        l.setLlaveHash(hash(llave));
        l.setCreadoEn(Instant.now());
        l = lectores.save(l);
        return Map.of("id", l.getId(), "nombre", l.getNombre(), "llave", llave);
    }

    public Map<String, Object> ligarTarjeta(String gymId, String epcCrudo, String userId, String duenoId) {
        String epc = normalizarEpc(epcCrudo);
        User socio = users.findById(userId == null ? "" : userId)
                .filter(u -> gymId.equals(u.getGymId()) && "member".equals(u.getRole()))
                .orElseThrow(() -> new TiendaException(HttpStatus.BAD_REQUEST, "Elige un socio de este gimnasio."));
        Tarjeta t = tarjetas.findByEpc(epc).orElseGet(Tarjeta::new);
        if (t.getId() != null && !gymId.equals(t.getGymId())) {
            throw new TiendaException(HttpStatus.CONFLICT, "Ese tag ya está registrado en otro gimnasio.");
        }
        t.setGymId(gymId);
        t.setEpc(epc);
        t.setUserId(socio.getId());
        if (t.getCreadaEn() == null) t.setCreadaEn(Instant.now());
        t = tarjetas.save(t);

        // Si el tag estaba capturado en modo registro, ya quedó asignado.
        for (Lector l : lectores.findByGymId(gymId)) {
            if (epc.equals(l.getEpcCapturado())) {
                l.setEpcCapturado(null);
                l.setEpcCapturadoEn(null);
                lectores.save(l);
            }
        }

        // Si la persona sigue en la puerta (su alerta está pendiente), registrarla
        // es dejarla pasar: entra con su nombre y su siguiente pase será la salida.
        boolean abrio = false;
        Instant ahora = Instant.now();
        for (SolicitudAcceso s : solicitudes.findTop20ByGymIdOrderByCreadaEnDesc(gymId)) {
            if (!epc.equals(s.getEpc()) || !SolicitudAcceso.PENDIENTE.equals(estadoDe(s, ahora))) continue;
            s.setUserId(socio.getId());
            s.setNombre(nombreDe(socio));
            solicitudes.save(s);
            if (!abrio) {
                atender(gymId, s.getId(), true, duenoId);
                abrio = true;
            } else {
                s.setEstado(SolicitudAcceso.APROBADA);
                s.setAtendidaEn(ahora);
                s.setAtendidaPor(duenoId);
                s.setComandoEntregado(true);   // ya se abrió con la primera
                solicitudes.save(s);
            }
        }
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("id", t.getId());
        r.put("epc", t.getEpc());
        r.put("userId", socio.getId());
        r.put("nombre", nombreDe(socio));
        r.put("abrio", abrio);
        return r;
    }

    public void iniciarRegistro(String gymId, String lectorId) {
        Lector l = lectorDelGimnasio(gymId, lectorId);
        l.setRegistroHasta(Instant.now().plus(MODO_REGISTRO));
        l.setEpcCapturado(null);
        l.setEpcCapturadoEn(null);
        lectores.save(l);
    }

    public void cancelarRegistro(String gymId, String lectorId) {
        Lector l = lectorDelGimnasio(gymId, lectorId);
        l.setRegistroHasta(null);
        l.setEpcCapturado(null);
        l.setEpcCapturadoEn(null);
        lectores.save(l);
    }

    private Lector lectorDelGimnasio(String gymId, String lectorId) {
        return lectores.findById(lectorId).filter(l -> gymId.equals(l.getGymId()))
                .orElseThrow(() -> new TiendaException(HttpStatus.NOT_FOUND, "Ese lector no existe."));
    }

    public void quitarTarjeta(String gymId, String id) {
        Tarjeta t = tarjetas.findById(id).filter(x -> gymId.equals(x.getGymId()))
                .orElseThrow(() -> new TiendaException(HttpStatus.NOT_FOUND, "Ese tag no está registrado."));
        tarjetas.delete(t);
    }

    public Map<String, Object> atender(String gymId, String solicitudId, boolean abrir, String duenoId) {
        SolicitudAcceso s = solicitudes.findById(solicitudId).filter(x -> gymId.equals(x.getGymId()))
                .orElseThrow(() -> new TiendaException(HttpStatus.NOT_FOUND, "Esa alerta no existe."));
        Instant ahora = Instant.now();
        String estado = estadoDe(s, ahora);
        if (!SolicitudAcceso.PENDIENTE.equals(estado)) {
            throw new TiendaException(HttpStatus.CONFLICT, "EXPIRADA".equals(estado)
                    ? "Pasaron más de 10 minutos: la persona ya no está en la puerta."
                    : "Esa alerta ya se atendió.");
        }
        s.setEstado(abrir ? SolicitudAcceso.APROBADA : SolicitudAcceso.RECHAZADA);
        s.setAtendidaEn(ahora);
        s.setAtendidaPor(duenoId);
        s.setComandoEntregado(false);
        solicitudes.save(s);
        if (abrir) {
            Lector lector = lectores.findById(s.getLectorId()).orElse(null);
            Acceso a = new Acceso();
            a.setGymId(gymId);
            a.setLectorId(s.getLectorId());
            a.setEpc(s.getEpc());
            a.setUserId(s.getUserId());
            a.setNombre(s.getNombre() == null ? "Visitante" : s.getNombre());
            a.setTipo(Acceso.ENTRADA);
            a.setMotivo("Abierta desde el panel" + (lector == null ? "" : " (" + lector.getNombre() + ")"));
            a.setSolicitudId(s.getId());
            a.setFecha(ahora);
            accesos.save(a);
        }
        return Map.of("id", s.getId(), "estado", s.getEstado());
    }

    public FotoAcceso foto(String gymId, String id) {
        return fotos.findById(id).filter(f -> gymId.equals(f.getGymId()))
                .orElseThrow(() -> new TiendaException(HttpStatus.NOT_FOUND, "Esa foto no existe."));
    }

    // ─────────────────────────── Reglas ───────────────────────────

    static boolean membresiaVigente(User u) {
        return Boolean.TRUE.equals(u.getMembershipActive())
                && (u.getFechaProximoPago() == null || !u.getFechaProximoPago().isBefore(Fechas.hoy()));
    }

    static boolean estaDentro(Optional<Acceso> ultimo, Instant ahora) {
        return ultimo.map(a -> Acceso.ENTRADA.equals(a.getTipo())
                && Duration.between(a.getFecha(), ahora).compareTo(SALIDA_AUTOMATICA) < 0).orElse(false);
    }

    private static String estadoDe(SolicitudAcceso s, Instant ahora) {
        if (SolicitudAcceso.PENDIENTE.equals(s.getEstado())
                && Duration.between(s.getCreadaEn(), ahora).compareTo(VIGENCIA_SOLICITUD) > 0) {
            return "EXPIRADA";
        }
        return s.getEstado();
    }

    private Map<String, User> socios(String gymId) {
        return users.findByGymId(gymId).stream()
                .filter(u -> "member".equals(u.getRole()))
                .collect(Collectors.toMap(User::getId, Function.identity(), (a, b) -> a));
    }

    private void guardarAcceso(Lector lector, String epc, String userId, String nombre, String tipo, String motivo, String solicitudId) {
        Acceso a = new Acceso();
        a.setGymId(lector.getGymId());
        a.setLectorId(lector.getId());
        a.setEpc(epc);
        a.setUserId(userId);
        a.setNombre(nombre);
        a.setTipo(tipo);
        a.setMotivo(motivo);
        a.setSolicitudId(solicitudId);
        a.setFecha(Instant.now());
        accesos.save(a);
    }

    private static Map<String, Object> respuesta(String resultado, String nombre, String motivo, String solicitudId) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("resultado", resultado);
        r.put("nombre", nombre == null ? "" : nombre);
        r.put("motivo", motivo == null ? "" : motivo);
        r.put("solicitudId", solicitudId);
        r.put("fotos", false);
        return r;
    }

    private static String nombreDe(User u) {
        return u.getNombre() == null || u.getNombre().isBlank() ? "(sin nombre)" : u.getNombre();
    }

    static String normalizarEpc(String epc) {
        String limpio = epc == null ? "" : epc.replaceAll("[\\s:-]", "").toUpperCase();
        if (!EPC.matcher(limpio).matches()) {
            throw new TiendaException(HttpStatus.BAD_REQUEST, "El EPC debe ser hexadecimal (8 a 64 caracteres).");
        }
        return limpio;
    }

    private static String hash(String llave) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(llave.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
