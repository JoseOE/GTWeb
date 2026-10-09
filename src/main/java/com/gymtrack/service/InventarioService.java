package com.gymtrack.service;

import com.gymtrack.model.Pedido;
import com.gymtrack.model.Producto;
import com.gymtrack.repository.ProductoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

// Stock de la tienda. Cada movimiento es UNA actualización atómica sobre el
// documento del producto, condicionada a que alcance (findAndModify con
// disponible >= cantidad): si dos personas compran la última pieza al mismo
// tiempo, MongoDB deja pasar solo a una y la otra recibe 409.
//
//  - apartar: tienda y app. La pieza queda vendida (o por pagar, en Paynet)
//    hasta que se entrega: baja disponible, sube apartadas.
//  - descontar: mostrador, que entrega en el acto: bajan disponible y existencias.
//  - devolverDePedido: un pago rechazado, un pedido cancelado, una ficha
//    vencida o un reembolso regresan lo que el pedido tomó, una sola vez.
//
// Las variantes sin control de inventario (scoops, planes) nunca se mueven.
@Service
public class InventarioService {

    private static final Logger log = LoggerFactory.getLogger(InventarioService.class);

    // Lo que una venta pide de una variante. titulo es para el mensaje si no alcanza.
    public record Solicitud(String productoId, String varianteId, int cantidad, String titulo) {}

    private enum Movimiento {
        // condición, disponible, apartadas, existencias (por pieza)
        APARTAR("disponible", -1, 1, 0),
        DESCONTAR("disponible", -1, 0, -1),
        LIBERAR("apartadas", 1, -1, 0),
        REPONER(null, 1, 0, 1);

        final String condicion;
        final int disponible, apartadas, existencias;

        Movimiento(String condicion, int disponible, int apartadas, int existencias) {
            this.condicion = condicion;
            this.disponible = disponible;
            this.apartadas = apartadas;
            this.existencias = existencias;
        }
    }

    private final MongoTemplate mongo;
    private final ProductoRepository productos;

    public InventarioService(MongoTemplate mongo, ProductoRepository productos) {
        this.mongo = mongo;
        this.productos = productos;
    }

    // Aparta todo o nada. Devuelve las piezas que de verdad se tomaron (las de
    // variantes con inventario), para guardarlas en el pedido.
    public List<Pedido.Pieza> apartar(List<Solicitud> solicitudes) {
        return tomar(solicitudes, Movimiento.APARTAR);
    }

    // Descuenta todo o nada (venta entregada en el acto).
    public List<Pedido.Pieza> descontar(List<Solicitud> solicitudes) {
        return tomar(solicitudes, Movimiento.DESCONTAR);
    }

    // Arma el registro de inventario del pedido.
    public static Pedido.Inventario registro(String estado, List<Pedido.Pieza> piezas) {
        Pedido.Inventario inv = new Pedido.Inventario();
        inv.setEstado(piezas.isEmpty() ? Pedido.Inventario.SIN_PIEZAS : estado);
        inv.setPiezas(piezas);
        return inv;
    }

    // Regresa lo que tomó el pedido: lo apartado vuelve a estar disponible y lo
    // entregado regresa a existencias. Primero se marca el pedido como devuelto
    // con una actualización condicionada, así dos llamadas a la vez (un
    // reembolso y el vencimiento, p. ej.) no lo devuelven dos veces.
    public boolean devolverDePedido(Pedido pedido) {
        if (pedido.getId() == null) return devolver(pedido.getInventario());
        Query query = Query.query(Criteria.where("_id").is(pedido.getId())
                .and("inventario.estado").in(Pedido.Inventario.APARTADO, Pedido.Inventario.DESCONTADO));
        Update update = new Update().set("inventario.estado", Pedido.Inventario.DEVUELTO).set("inventario.devueltoEn", Instant.now());
        Pedido antes = mongo.findAndModify(query, update, Pedido.class);
        if (antes == null) return false;
        devolver(antes.getInventario());
        if (pedido.getInventario() != null) {
            pedido.getInventario().setEstado(Pedido.Inventario.DEVUELTO);
            pedido.getInventario().setDevueltoEn(Instant.now());
        }
        return true;
    }

    // Lo mismo para piezas que todavía no tienen pedido (el pago se rechazó).
    public boolean devolver(Pedido.Inventario inventario) {
        if (inventario == null || inventario.getPiezas() == null) return false;
        Movimiento m = Pedido.Inventario.DESCONTADO.equals(inventario.getEstado()) ? Movimiento.REPONER
                : Pedido.Inventario.APARTADO.equals(inventario.getEstado()) ? Movimiento.LIBERAR : null;
        if (m == null) return false;
        for (Pedido.Pieza p : inventario.getPiezas()) {
            if (!mover(p.getProductoId(), p.getVarianteId(), p.getCantidad(), m)) {
                // La variante se borró o dejó de llevar inventario: no hay a dónde regresarla.
                log.info("No se regresaron {} pieza(s) de la variante {}: ya no lleva inventario.", p.getCantidad(), p.getVarianteId());
            }
        }
        return true;
    }

    // ─── Por dentro ───

    private List<Pedido.Pieza> tomar(List<Solicitud> solicitudes, Movimiento m) {
        List<Pedido.Pieza> tomadas = new ArrayList<>();
        for (Solicitud s : solicitudes) {
            if (s.cantidad() <= 0) continue;
            if (mover(s.productoId(), s.varianteId(), s.cantidad(), m)) {
                tomadas.add(pieza(s));
                continue;
            }
            // No alcanzó, o la variante cambió: se averigua por qué antes de responder.
            Producto.Variante actual = productos.findById(s.productoId()).map(p -> p.variante(s.varianteId())).orElse(null);
            if (actual != null && !actual.isControlarInventario()) continue;
            regresar(tomadas, m);
            if (actual == null) {
                throw new TiendaException(HttpStatus.CONFLICT, "«" + s.titulo() + "» ya no está a la venta.");
            }
            throw new TiendaException(HttpStatus.CONFLICT, "Ya no hay suficientes piezas de «" + s.titulo() + "».");
        }
        return tomadas;
    }

    // Deshace lo que alcanzó a tomar una venta que no se completó.
    private void regresar(List<Pedido.Pieza> tomadas, Movimiento m) {
        Movimiento inverso = m == Movimiento.APARTAR ? Movimiento.LIBERAR : Movimiento.REPONER;
        for (Pedido.Pieza p : tomadas) mover(p.getProductoId(), p.getVarianteId(), p.getCantidad(), inverso);
    }

    private boolean mover(String productoId, String varianteId, int cantidad, Movimiento m) {
        Criteria variante = Criteria.where("id").is(varianteId).and("controlarInventario").is(true);
        if (m.condicion != null) variante = variante.and(m.condicion).gte(cantidad);
        Query query = Query.query(Criteria.where("_id").is(productoId).and("variantes").elemMatch(variante));
        Update update = new Update()
                .inc("variantes.$.disponible", m.disponible * cantidad)
                .inc("version", 1);
        if (m.apartadas != 0) update.inc("variantes.$.apartadas", m.apartadas * cantidad);
        if (m.existencias != 0) update.inc("variantes.$.existencias", m.existencias * cantidad);
        return mongo.findAndModify(query, update, FindAndModifyOptions.options().returnNew(false), Producto.class) != null;
    }

    private static Pedido.Pieza pieza(Solicitud s) {
        Pedido.Pieza p = new Pedido.Pieza();
        p.setProductoId(s.productoId());
        p.setVarianteId(s.varianteId());
        p.setCantidad(s.cantidad());
        return p;
    }
}
