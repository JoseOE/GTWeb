package com.gymtrack.service;

import com.gymtrack.model.EventoSeguridad;
import com.gymtrack.model.Gym;
import com.gymtrack.model.Opinion;
import com.gymtrack.model.Pedido;
import com.gymtrack.model.Producto;
import com.gymtrack.model.Resena;
import com.gymtrack.model.User;
import com.gymtrack.repository.GymRepository;
import com.gymtrack.repository.OpinionRepository;
import com.gymtrack.repository.ProductoRepository;
import com.gymtrack.repository.ResenaRepository;
import com.gymtrack.repository.UserRepository;
import com.gymtrack.util.PasswordUtil;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

// Panel de administración del equipo de GymTrack: ve todos los gimnasios, las
// cuentas, las ventas, las reseñas (buenas y malas, ocultas incluidas) y la
// bitácora de toda la plataforma, y puede ocultar una reseña u opinión.
//
// La cuenta no se registra en la página: se crea (o se pone al día) al arrancar
// con ADMIN_EMAIL y ADMIN_PASSWORD. Así la contraseña vive solo en el .env y en
// Render, como las demás llaves, y para cambiarla basta con cambiar la variable
// y reiniciar. Sin esas variables no hay administrador. Una contraseña que no
// cumple la política (8+ caracteres con letra, número y símbolo) se rechaza.
//
// Su sesión dura 8 horas en lugar de 30 días, y cada inicio de sesión y cada
// cosa que oculta queda en la bitácora.
@Service
public class AdministradorService implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdministradorService.class);

    public static final String ROL = "admin";
    public static final Duration VIGENCIA_SESION = Duration.ofHours(8);
    static final int MAX_USUARIOS = 2000;
    static final int MAX_RESENAS = 1000;

    private final UserRepository users;
    private final GymRepository gyms;
    private final ResenaRepository resenas;
    private final OpinionRepository opiniones;
    private final ProductoRepository productos;
    private final MongoTemplate mongo;
    private final String email;
    private final String password;
    private final String nombre;

    public AdministradorService(UserRepository users, GymRepository gyms, ResenaRepository resenas,
                                OpinionRepository opiniones, ProductoRepository productos, MongoTemplate mongo,
                                @Value("${admin.email:}") String email,
                                @Value("${admin.password:}") String password,
                                @Value("${admin.nombre:Equipo GymTrack}") String nombre) {
        this.users = users;
        this.gyms = gyms;
        this.resenas = resenas;
        this.opiniones = opiniones;
        this.productos = productos;
        this.mongo = mongo;
        this.email = email == null ? "" : email.trim().toLowerCase();
        this.password = password == null ? "" : password;
        this.nombre = nombre;
    }

    // ═══════════════════════════ LA CUENTA ═══════════════════════════

    @Override
    public void run(ApplicationArguments args) {
        if (email.isBlank() || password.isBlank()) {
            log.info("Sin ADMIN_EMAIL y ADMIN_PASSWORD: no hay cuenta de administrador.");
            return;
        }
        if (!PasswordUtil.esSegura(password)) {
            log.error("ADMIN_PASSWORD no cumple la política (8+ caracteres con letra, número y símbolo): no se creó el administrador.");
            return;
        }
        User admin = users.findByEmail(email).orElse(null);
        if (admin != null && !ROL.equals(admin.getRole())) {
            // Nunca se convierte en administrador una cuenta que ya existía.
            log.error("ADMIN_EMAIL ({}) ya es una cuenta de dueño o miembro: usa otro correo para el administrador.", email);
            return;
        }
        boolean nueva = admin == null;
        if (nueva) {
            admin = new User();
            admin.setEmail(email);
            admin.setNombre(nombre);
            admin.setRole(ROL);
            admin.setGymId(null);
        }
        if (nueva || !PasswordUtil.coincide(password, admin.getPassword())) {
            admin.setPassword(PasswordUtil.hash(password));
        }
        admin.setEmailVerificado(true);
        admin.setContrasenaTemporal(false);
        users.save(admin);
        log.info(nueva ? "Cuenta de administrador creada: {}" : "Cuenta de administrador al día: {}", email);
    }

    public User exigirAdmin(User user) {
        if (user == null || !ROL.equals(user.getRole())) {
            throw new TiendaException(HttpStatus.FORBIDDEN, "Solo el equipo de GymTrack puede ver esto.");
        }
        return user;
    }

    // ═══════════════════════════ RESUMEN ═══════════════════════════

    public Map<String, Object> resumen() {
        Instant hace30 = Instant.now().minus(Duration.ofDays(30));
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("gimnasios", gyms.count());
        v.put("duenos", contar(User.class, Criteria.where("role").is("owner")));
        v.put("miembros", contar(User.class, Criteria.where("role").is("member")));
        v.put("cuentasNuevas30", contar(User.class, Criteria.where("_id").gte(new ObjectId(java.util.Date.from(hace30)))));

        Document ventas = suma(Criteria.where("estado").is(Pedido.ESTADO_PAGADO));
        Document ventas30 = suma(Criteria.where("estado").is(Pedido.ESTADO_PAGADO).and("pagadoEn").gte(hace30));
        v.put("pedidosPagados", ventas.get("pedidos", 0));
        v.put("ventasTotal", redondear(ventas.get("total")));
        v.put("ventas30", redondear(ventas30.get("total")));
        v.put("pedidos30", ventas30.get("pedidos", 0));

        v.put("resenas", calificacion(Resena.class, "estrellas"));
        v.put("opiniones", calificacion(Opinion.class, "estrellas"));
        return v;
    }

    // ═══════════════════════════ GIMNASIOS ═══════════════════════════

    public List<Map<String, Object>> gimnasios() {
        Map<String, Document> ventas = porGimnasio(Pedido.class, Criteria.where("estado").is(Pedido.ESTADO_PAGADO), "total");
        Map<String, Document> miembros = porGimnasio(User.class, Criteria.where("role").is("member"), null);
        Map<String, Document> resenasGym = porGimnasio(Resena.class, null, "estrellas");
        Map<String, Document> opinionesGym = porGimnasio(Opinion.class, null, "estrellas");
        Map<String, User> duenos = users.findAll().stream().filter(u -> "owner".equals(u.getRole()))
                .collect(Collectors.toMap(User::getId, Function.identity(), (a, b) -> a));

        return gyms.findAll().stream().map(g -> {
            Map<String, Object> v = new LinkedHashMap<>();
            v.put("id", g.getId());
            v.put("nombre", g.getNombre());
            v.put("foto", g.getFoto());
            v.put("logo", g.getLogo());
            v.put("colorPrimario", g.getColorPrimario());
            v.put("direccion", g.getDireccion());
            v.put("enDirectorio", Boolean.TRUE.equals(g.getEnDirectorio()));
            v.put("creado", creado(g.getId()));
            User d = duenos.get(g.getOwnerId());
            v.put("dueno", d == null ? null : Map.of("nombre", texto(d.getNombre()), "email", texto(d.getEmail())));
            v.put("miembros", cuenta(miembros.get(g.getId())));
            Document vt = ventas.get(g.getId());
            v.put("pedidosPagados", cuenta(vt));
            v.put("ventas", vt == null ? 0.0 : redondear(vt.get("suma")));
            v.put("resenas", promedio(resenasGym.get(g.getId())));
            v.put("opiniones", promedio(opinionesGym.get(g.getId())));
            return v;
        }).sorted(Comparator.comparing((Map<String, Object> m) -> (Double) m.get("ventas")).reversed()).toList();
    }

    // ═══════════════════════════ CUENTAS ═══════════════════════════

    // Todas las cuentas (sin contraseñas ni tokens), la más nueva primero, con
    // su gimnasio y su último inicio de sesión (de la bitácora, 180 días).
    public List<Map<String, Object>> usuarios() {
        Map<String, String> nombresGym = gyms.findAll().stream()
                .collect(Collectors.toMap(Gym::getId, g -> texto(g.getNombre()), (a, b) -> a));
        Map<String, Object> ultimos = new HashMap<>();
        mongo.aggregate(Aggregation.newAggregation(
                        Aggregation.match(Criteria.where("tipo").is(BitacoraService.LOGIN_EXITOSO).and("userId").ne(null)),
                        Aggregation.group("userId").max("fecha").as("ultimo")),
                EventoSeguridad.class, Document.class).getMappedResults()
                .forEach(d -> ultimos.put(String.valueOf(d.get("_id")), d.get("ultimo")));

        Query q = new Query().with(Sort.by(Sort.Direction.DESC, "_id")).limit(MAX_USUARIOS);
        q.fields().exclude("password", "pushToken");
        return mongo.find(q, User.class).stream().map(u -> {
            Map<String, Object> v = new LinkedHashMap<>();
            v.put("id", u.getId());
            v.put("nombre", u.getNombre());
            v.put("email", u.getEmail());
            v.put("rol", u.getRole());
            v.put("gymId", u.getGymId());
            v.put("gimnasio", u.getGymId() == null ? null : nombresGym.get(u.getGymId()));
            v.put("estado", u.getMembershipStatus());
            v.put("correoVerificado", !Boolean.FALSE.equals(u.getEmailVerificado()));
            v.put("demo", u.getEmail() != null && u.getEmail().endsWith("@" + CorreoService.DOMINIO_DEMO));
            v.put("creado", creado(u.getId()));
            v.put("ultimoAcceso", ultimos.get(u.getId()));
            return v;
        }).toList();
    }

    // ═══════════════════════════ RESEÑAS ═══════════════════════════

    // Reseñas de productos y opiniones de los gimnasios en una sola lista, las
    // más recientes primero: buenas, malas y ocultas. Aquí sí va el nombre
    // completo y el correo de quien escribió, para poder darle seguimiento.
    public List<Map<String, Object>> resenas() {
        Map<String, User> autores = new HashMap<>();
        Function<String, User> autor = id -> id == null ? null : autores.computeIfAbsent(id, k -> users.findById(k).orElse(null));
        Map<String, String> nombresGym = gyms.findAll().stream()
                .collect(Collectors.toMap(Gym::getId, g -> texto(g.getNombre()), (a, b) -> a));

        List<Map<String, Object>> lista = new ArrayList<>();
        List<Resena> deProductos = mongo.find(new Query().with(Sort.by(Sort.Direction.DESC, "actualizadoEn")).limit(MAX_RESENAS), Resena.class);
        Map<String, String> nombresProducto = new HashMap<>();
        productos.findAllById(deProductos.stream().map(Resena::getProductoId).distinct().toList())
                .forEach(p -> nombresProducto.put(p.getId(), p.getNombre()));
        for (Resena r : deProductos) {
            User u = autor.apply(r.getUserId());
            Map<String, Object> v = base("producto", r.getId(), r.getEstrellas(), r.getComentario(), r.isOculta(),
                    r.getActualizadoEn() != null ? r.getActualizadoEn() : r.getCreadoEn(), u, r.getAutor());
            v.put("gymId", r.getGymId());
            v.put("gimnasio", nombresGym.getOrDefault(r.getGymId(), "Gimnasio eliminado"));
            v.put("producto", nombresProducto.getOrDefault(r.getProductoId(), "Producto eliminado"));
            lista.add(v);
        }
        for (Opinion o : mongo.find(new Query().with(Sort.by(Sort.Direction.DESC, "actualizadaEn")).limit(MAX_RESENAS), Opinion.class)) {
            User u = autor.apply(o.getUserId());
            Map<String, Object> v = base("gimnasio", o.getId(), o.getEstrellas(), o.getTexto(), o.isOculta(),
                    o.getActualizadaEn() != null ? o.getActualizadaEn() : o.getCreadaEn(), u, null);
            v.put("rolAutor", "owner".equals(o.getRol()) ? "Dueño" : "Miembro");
            v.put("gymId", o.getGymId());
            v.put("gimnasio", nombresGym.getOrDefault(o.getGymId(), "Gimnasio eliminado"));
            v.put("enCarrusel", !o.isOculta() && o.getEstrellas() >= OpinionService.MINIMO_DESTACADA);
            lista.add(v);
        }
        lista.sort(Comparator.comparing((Map<String, Object> m) -> (Instant) m.get("fecha"),
                Comparator.nullsLast(Comparator.naturalOrder())).reversed());
        return lista;
    }

    public Map<String, Object> ocultarResena(String id, boolean oculta) {
        Resena r = resenas.findById(id).orElseThrow(() -> new TiendaException(HttpStatus.NOT_FOUND, "Reseña no encontrada."));
        r.setOculta(oculta);
        resenas.save(r);
        return Map.of("id", r.getId(), "oculta", r.isOculta());
    }

    public Map<String, Object> ocultarOpinion(String id, boolean oculta) {
        Opinion o = opiniones.findById(id).orElseThrow(() -> new TiendaException(HttpStatus.NOT_FOUND, "Opinión no encontrada."));
        o.setOculta(oculta);
        opiniones.save(o);
        return Map.of("id", o.getId(), "oculta", o.isOculta(),
                "enCarrusel", !o.isOculta() && o.getEstrellas() >= OpinionService.MINIMO_DESTACADA);
    }

    // ═══════════════════════════ BITÁCORA ═══════════════════════════

    // La de toda la plataforma, lo más reciente primero, con el gimnasio.
    public List<Map<String, Object>> bitacora(int limite) {
        Map<String, String> nombresGym = gyms.findAll().stream()
                .collect(Collectors.toMap(Gym::getId, g -> texto(g.getNombre()), (a, b) -> a));
        Query q = new Query().with(Sort.by(Sort.Direction.DESC, "fecha")).limit(Math.max(1, Math.min(limite, 1000)));
        return mongo.find(q, EventoSeguridad.class).stream().map(e -> {
            Map<String, Object> v = new LinkedHashMap<>();
            v.put("fecha", e.getFecha());
            v.put("tipo", e.getTipo());
            v.put("descripcion", e.getDescripcion());
            v.put("userId", e.getUserId());
            v.put("email", e.getEmail());
            v.put("gimnasio", e.getGymId() == null ? null : nombresGym.get(e.getGymId()));
            v.put("metodo", e.getMetodo());
            v.put("ruta", e.getRuta());
            v.put("estado", e.getEstado());
            v.put("ip", e.getIp());
            v.put("agente", e.getAgente());
            return v;
        }).toList();
    }

    // ═══════════════════════════ AYUDANTES ═══════════════════════════

    private static Map<String, Object> base(String tipo, String id, int estrellas, String texto, boolean oculta,
                                            Instant fecha, User u, String autorGuardado) {
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("tipo", tipo);
        v.put("id", id);
        v.put("estrellas", estrellas);
        v.put("texto", texto);
        v.put("oculta", oculta);
        v.put("fecha", fecha);
        v.put("autor", u != null ? u.getNombre() : autorGuardado != null ? autorGuardado : "Cuenta eliminada");
        v.put("email", u == null ? null : u.getEmail());
        return v;
    }

    private long contar(Class<?> tipo, Criteria criterio) {
        return mongo.count(Query.query(criterio), tipo);
    }

    private Document suma(Criteria criterio) {
        List<Document> r = mongo.aggregate(Aggregation.newAggregation(
                Aggregation.match(criterio),
                Aggregation.group().count().as("pedidos").sum("total").as("total")), Pedido.class, Document.class).getMappedResults();
        return r.isEmpty() ? new Document() : r.get(0);
    }

    // {promedio, total, malas (1-2★), regulares (3★), buenas (4-5★)}
    private Map<String, Object> calificacion(Class<?> tipo, String campo) {
        List<Document> r = mongo.aggregate(Aggregation.newAggregation(
                Aggregation.group(campo).count().as("n")), tipo, Document.class).getMappedResults();
        long total = 0, malas = 0, regulares = 0, buenas = 0, suma = 0;
        for (Document d : r) {
            if (!(d.get("_id") instanceof Number num)) continue;
            int e = num.intValue();
            long n = ((Number) d.get("n")).longValue();
            total += n;
            suma += (long) e * n;
            if (e <= 2) malas += n; else if (e == 3) regulares += n; else buenas += n;
        }
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("total", total);
        v.put("promedio", total == 0 ? 0.0 : Math.round(suma * 10.0 / total) / 10.0);
        v.put("buenas", buenas);
        v.put("regulares", regulares);
        v.put("malas", malas);
        return v;
    }

    // {gymId → {n, suma}}; suma solo si se pide un campo.
    private Map<String, Document> porGimnasio(Class<?> tipo, Criteria criterio, String campo) {
        var grupo = Aggregation.group("gymId").count().as("n");
        if (campo != null) grupo = grupo.sum(campo).as("suma");
        Map<String, Document> m = new HashMap<>();
        mongo.aggregate(Aggregation.newAggregation(Aggregation.match(criterio == null ? Criteria.where("gymId").ne(null) : criterio.and("gymId").ne(null)), grupo), tipo, Document.class)
                .getMappedResults().forEach(d -> m.put(String.valueOf(d.get("_id")), d));
        return m;
    }

    private static long cuenta(Document d) {
        return d == null ? 0 : ((Number) d.get("n")).longValue();
    }

    private static Map<String, Object> promedio(Document d) {
        long n = cuenta(d);
        double suma = d == null || d.get("suma") == null ? 0 : ((Number) d.get("suma")).doubleValue();
        return Map.of("total", n, "promedio", n == 0 ? 0.0 : Math.round(suma * 10.0 / n) / 10.0);
    }

    private static double redondear(Object valor) {
        return valor instanceof Number n ? Math.round(n.doubleValue() * 100.0) / 100.0 : 0.0;
    }

    private static String texto(String s) {
        return s == null ? "" : s;
    }

    // Las cuentas y los gimnasios no guardan su fecha de alta, pero su id de
    // Mongo (ObjectId) sí la trae.
    static Instant creado(String id) {
        return id != null && ObjectId.isValid(id) ? new ObjectId(id).getDate().toInstant() : null;
    }
}
