package com.gymtrack.service;

import com.gymtrack.model.Pedido;
import com.gymtrack.model.Producto;
import com.gymtrack.model.Resena;
import com.gymtrack.model.User;
import com.gymtrack.repository.ProductoRepository;
import com.gymtrack.repository.ResenaRepository;
import org.bson.Document;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// Reseñas de los productos de la tienda.
//  - Solo califica quien compró: un pedido pagado suyo que incluye el producto.
//  - Una por persona y producto; volver a calificar edita la que ya tenía.
//  - Las ocultas por el dueño no cuentan en el promedio ni se muestran.
//  - El carrusel de la tienda junta los productos de ese gimnasio con promedio
//    mayor a 3.8 estrellas.
// Los planes de membresía no se califican: son productos de tipo "membresia".
@Service
public class ResenaService {

    public static final double MINIMO_CARRUSEL = 3.8;
    private static final int MAX_COMENTARIO = 500;
    private static final int POR_PAGINA = 10;
    private static final int MAX_CARRUSEL = 12;

    private final ResenaRepository resenas;
    private final ProductoRepository productos;
    private final MongoTemplate mongo;

    public ResenaService(ResenaRepository resenas, ProductoRepository productos, MongoTemplate mongo) {
        this.resenas = resenas;
        this.productos = productos;
        this.mongo = mongo;
    }

    // Promedio y total de reseñas visibles de un producto.
    public record Calificacion(double promedio, int total) {
        Map<String, Object> vista() {
            Map<String, Object> v = new LinkedHashMap<>();
            v.put("promedio", Math.round(promedio * 10) / 10.0);
            v.put("total", total);
            return v;
        }
    }

    // ═══════════════════════════ LECTURA ═══════════════════════════

    // Calificación de cada producto de un gimnasio que tiene reseñas: una sola
    // agregación, para pintar las estrellas en las tarjetas de la tienda.
    public Map<String, Calificacion> calificaciones(String gymId) {
        Aggregation a = Aggregation.newAggregation(
                Aggregation.match(Criteria.where("gymId").is(gymId).and("oculta").is(false)),
                Aggregation.group("productoId").avg("estrellas").as("promedio").count().as("total"));
        Map<String, Calificacion> m = new HashMap<>();
        for (Document d : mongo.aggregate(a, Resena.class, Document.class).getMappedResults()) {
            m.put(d.getString("_id"), new Calificacion(((Number) d.get("promedio")).doubleValue(), ((Number) d.get("total")).intValue()));
        }
        return m;
    }

    // Lo que ve producto.html: el resumen con la distribución por estrellas,
    // una página de reseñas y, si quien pregunta lo compró, su propia reseña.
    public Map<String, Object> deProducto(User user, String gymId, String productoId, int pagina) {
        Producto p = exigirProducto(gymId, productoId);
        Map<Integer, Integer> porEstrellas = new LinkedHashMap<>();
        for (int e = 5; e >= 1; e--) porEstrellas.put(e, 0);
        int total = 0;
        double suma = 0;
        Aggregation a = Aggregation.newAggregation(
                Aggregation.match(Criteria.where("productoId").is(p.getId()).and("oculta").is(false)),
                Aggregation.group("estrellas").count().as("total"));
        for (Document d : mongo.aggregate(a, Resena.class, Document.class).getMappedResults()) {
            int estrellas = ((Number) d.get("_id")).intValue();
            int cuantas = ((Number) d.get("total")).intValue();
            porEstrellas.put(estrellas, cuantas);
            total += cuantas;
            suma += (double) estrellas * cuantas;
        }
        Map<String, Object> resumen = new Calificacion(total == 0 ? 0 : suma / total, total).vista();
        resumen.put("porEstrellas", porEstrellas);

        List<Map<String, Object>> lista = resenas
                .findByProductoIdAndOcultaFalseOrderByActualizadoEnDesc(p.getId(), PageRequest.of(Math.max(0, pagina), POR_PAGINA))
                .stream().map(r -> vista(r, user)).toList();

        Map<String, Object> v = new LinkedHashMap<>();
        v.put("calificacion", resumen);
        v.put("resenas", lista);
        v.put("hayMas", (long) (Math.max(0, pagina) + 1) * POR_PAGINA < total);
        v.put("mia", mia(user, p));
        return v;
    }

    // Carrusel de la tienda: productos publicados del gimnasio con promedio
    // mayor a 3.8, los mejor calificados primero, cada uno con una reseña de
    // muestra (la más reciente de 4 o 5 estrellas que traiga comentario).
    public List<Map<String, Object>> destacados(String gymId) {
        Map<String, Calificacion> cal = calificaciones(gymId);
        List<Producto> candidatos = productos.findByGymIdAndActivoTrue(gymId).stream()
                .filter(p -> !p.esPlan())
                .filter(p -> cal.containsKey(p.getId()) && cal.get(p.getId()).promedio() > MINIMO_CARRUSEL)
                .sorted(Comparator.comparingDouble((Producto p) -> -cal.get(p.getId()).promedio())
                        .thenComparingInt(p -> -cal.get(p.getId()).total()))
                .limit(MAX_CARRUSEL)
                .toList();
        List<Map<String, Object>> lista = new ArrayList<>();
        for (Producto p : candidatos) {
            Map<String, Object> v = EscaparateService.vista(p);
            v.put("calificacion", cal.get(p.getId()).vista());
            Resena muestra = mongo.findOne(Query.query(Criteria.where("productoId").is(p.getId()).and("oculta").is(false)
                            .and("estrellas").gte(4).and("comentario").ne(null))
                    .with(Sort.by(Sort.Direction.DESC, "actualizadoEn")), Resena.class);
            v.put("resena", muestra == null ? null : Map.of("autor", muestra.getAutor(), "estrellas", muestra.getEstrellas(), "comentario", muestra.getComentario()));
            lista.add(v);
        }
        return lista;
    }

    // ═══════════════════════════ ESCRITURA ═══════════════════════════

    public Map<String, Object> guardar(User user, String gymId, String productoId, Integer estrellas, String comentario) {
        Producto p = exigirProducto(gymId, productoId);
        if (!compro(user, p)) {
            throw new TiendaException(HttpStatus.FORBIDDEN, "Solo puedes calificar productos que ya compraste y pagaste.");
        }
        if (estrellas == null || estrellas < 1 || estrellas > 5) {
            throw new TiendaException(HttpStatus.BAD_REQUEST, "Elige de 1 a 5 estrellas.");
        }
        String texto = comentario == null ? "" : comentario.trim();
        if (texto.length() > MAX_COMENTARIO) {
            throw new TiendaException(HttpStatus.BAD_REQUEST, "El comentario puede tener hasta " + MAX_COMENTARIO + " caracteres.");
        }
        Instant ahora = Instant.now();
        Resena r = resenas.findByUserIdAndProductoId(user.getId(), p.getId()).orElseGet(() -> {
            Resena nueva = new Resena();
            nueva.setUserId(user.getId());
            nueva.setProductoId(p.getId());
            nueva.setGymId(p.getGymId());
            nueva.setCreadoEn(ahora);
            return nueva;
        });
        r.setAutor(firma(user.getNombre()));
        r.setEstrellas(estrellas);
        r.setComentario(texto.isEmpty() ? null : texto);
        r.setActualizadoEn(ahora);
        try {
            resenas.save(r);
        } catch (DuplicateKeyException e) {
            // Dos envíos a la vez: el otro ya la creó; se actualiza esa.
            return guardar(user, gymId, productoId, estrellas, comentario);
        }
        return mia(user, p);
    }

    public Map<String, Object> borrar(User user, String gymId, String productoId) {
        Producto p = exigirProducto(gymId, productoId);
        resenas.findByUserIdAndProductoId(user.getId(), p.getId()).ifPresent(resenas::delete);
        return mia(user, p);
    }

    // ═══════════════════════════ PANEL ═══════════════════════════

    // Todas las reseñas del gimnasio, ocultas incluidas, con el nombre del producto.
    public List<Map<String, Object>> delGimnasio(String gymId) {
        Map<String, String> nombres = new HashMap<>();
        productos.findByGymIdAndTipo(gymId, Producto.TIPO_PRODUCTO).forEach(p -> nombres.put(p.getId(), p.getNombre()));
        return resenas.findByGymIdOrderByActualizadoEnDesc(gymId, PageRequest.of(0, 200)).stream().map(r -> {
            Map<String, Object> v = vista(r, null);
            v.put("producto", nombres.getOrDefault(r.getProductoId(), "Producto eliminado"));
            v.put("productoId", r.getProductoId());
            v.put("oculta", r.isOculta());
            return v;
        }).toList();
    }

    public Map<String, Object> cambiarVisibilidad(String gymId, String resenaId, boolean oculta) {
        Resena r = resenas.findByIdAndGymId(resenaId, gymId)
                .orElseThrow(() -> new TiendaException(HttpStatus.NOT_FOUND, "Reseña no encontrada."));
        r.setOculta(oculta);
        resenas.save(r);
        Map<String, Object> v = vista(r, null);
        v.put("oculta", r.isOculta());
        return v;
    }

    // ═══════════════════════════ AYUDANTES ═══════════════════════════

    private Map<String, Object> mia(User user, Producto p) {
        Map<String, Object> v = new LinkedHashMap<>();
        boolean puede = user != null && compro(user, p);
        v.put("puede", puede);
        v.put("resena", user == null ? null : resenas.findByUserIdAndProductoId(user.getId(), p.getId())
                .map(r -> {
                    Map<String, Object> x = vista(r, user);
                    x.put("oculta", r.isOculta());
                    return x;
                }).orElse(null));
        return v;
    }

    private boolean compro(User user, Producto p) {
        return mongo.exists(Query.query(Criteria.where("userId").is(user.getId())
                .and("estado").is(Pedido.ESTADO_PAGADO)
                .and("partidas.productoId").is(p.getId())), Pedido.class);
    }

    private Producto exigirProducto(String gymId, String productoId) {
        return productos.findByIdAndGymId(productoId, gymId)
                .filter(p -> !p.esPlan())
                .orElseThrow(() -> new TiendaException(HttpStatus.NOT_FOUND, "Ese producto no está en esta tienda."));
    }

    private static Map<String, Object> vista(Resena r, User user) {
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("id", r.getId());
        v.put("autor", r.getAutor());
        v.put("estrellas", r.getEstrellas());
        v.put("comentario", r.getComentario());
        v.put("fecha", r.getActualizadoEn());
        v.put("editada", r.getCreadoEn() != null && r.getActualizadoEn() != null && r.getActualizadoEn().isAfter(r.getCreadoEn().plusSeconds(60)));
        v.put("propia", user != null && user.getId().equals(r.getUserId()));
        return v;
    }

    // "Ana María Pérez López" → "Ana P.": nombre y la inicial del apellido.
    static String firma(String nombre) {
        if (nombre == null || nombre.isBlank()) return "Miembro del gimnasio";
        String[] partes = nombre.trim().split("\\s+");
        if (partes.length == 1) return partes[0];
        // Con 3 o más palabras, la segunda suele ser otro nombre: se toma la inicial de la penúltima.
        String apellido = partes.length >= 3 ? partes[partes.length - 2] : partes[1];
        return partes[0] + " " + Character.toUpperCase(apellido.charAt(0)) + ".";
    }
}
