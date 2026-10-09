package com.gymtrack.service;

import com.gymtrack.model.Pedido;
import com.gymtrack.model.User;
import com.gymtrack.util.MetodosPago;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

// Venta en el mostrador del panel. El ticket se arma en el navegador, así que
// agregar y quitar productos no espera al servidor; al cobrar llega completo,
// se revisa contra lo que hoy está a la venta y se cobra de una vez con el
// mismo PedidoService que la tienda: descuenta existencias en el acto, porque
// el producto se entrega en recepción.
@Service
public class MostradorService {

    private final CarritoService carritos;
    private final PedidoService pedidos;
    private final SimuladorStripeService stripe;

    public MostradorService(CarritoService carritos, PedidoService pedidos, SimuladorStripeService stripe) {
        this.carritos = carritos;
        this.pedidos = pedidos;
        this.stripe = stripe;
    }

    // Una partida del ticket del mostrador, tal como la manda el panel.
    public record PartidaTicket(String varianteId, Integer cantidad) {}

    public record VentaMostrador(Pedido pedido, Double cambio) {}

    // Venta en el mostrador del panel: la cobra el dueño, en efectivo o con la
    // tarjeta (el simulador de Stripe hace de terminal), a un miembro o al
    // público en general, y se entrega en el acto (descuenta existencias).
    public VentaMostrador cobrar(User dueno, String gymId, User cliente, List<PartidaTicket> partidas,
                                String metodo, Double recibido, Map<String, Object> datos, Double totalVisto) {
        if (!"efectivo".equals(metodo) && !"tarjeta".equals(metodo)) {
            throw new TiendaException(HttpStatus.BAD_REQUEST, "Elige cobrar en efectivo o con tarjeta.");
        }
        List<PedidoService.Linea> lineas = revisarTicket(carritos.catalogo(gymId), partidas, totalVisto);
        double total = PedidoService.centavos(lineas.stream().mapToDouble(l -> l.precioUnitario() * l.cantidad()).sum());
        if ("efectivo".equals(metodo) && (recibido == null || recibido + 0.009 < total)) {
            throw new TiendaException(HttpStatus.BAD_REQUEST, "El efectivo recibido no alcanza para " + CarritoService.dinero(total) + ".");
        }
        String proveedor;
        Double cambio = null;
        Map<String, Object> extras = null;
        if ("efectivo".equals(metodo)) {
            proveedor = MetodosPago.EFECTIVO;
            cambio = PedidoService.centavos(recibido - total);
            extras = Map.of("recibido", recibido, "cambio", cambio);
            datos = Map.of();
        } else {
            proveedor = MetodosPago.STRIPE;
            datos = stripe.consumir(dueno.getId(), datos);
        }
        // En una venta al público el correo del pedido es el del dueño y no se
        // manda ningún aviso.
        Pedido pedido = pedidos.cobrar(new PedidoService.Venta(gymId, Pedido.CANAL_MOSTRADOR,
                cliente == null ? null : cliente.getId(),
                cliente == null ? dueno.getEmail() : cliente.getEmail(),
                cliente == null ? "Público en general" : cliente.getNombre(),
                dueno.getId(), lineas, proveedor, datos, true, extras));
        return new VentaMostrador(pedido, cambio);
    }

    // Revisa el ticket del mostrador contra lo que hoy está a la venta: solo lo
    // publicado, nunca más piezas de las disponibles y un solo plan que
    // extienda la membresía. Si algo cambió, se avisa y no se cobra.
    private List<PedidoService.Linea> revisarTicket(Map<String, CarritoService.Elegible> cat, List<PartidaTicket> partidas, Double totalVisto) {
        if (partidas == null || partidas.isEmpty()) {
            throw new TiendaException(HttpStatus.BAD_REQUEST, "El ticket está vacío.");
        }
        // Una sola partida por presentación, en el orden en que se agregaron.
        Map<String, Integer> cantidades = new LinkedHashMap<>();
        for (PartidaTicket p : partidas) {
            if (p == null || p.varianteId() == null || p.varianteId().isBlank()) {
                throw new TiendaException(HttpStatus.BAD_REQUEST, "Hay una partida sin presentación en el ticket.");
            }
            cantidades.merge(p.varianteId(), p.cantidad() == null ? 1 : p.cantidad(), Integer::sum);
        }
        List<PedidoService.Linea> lineas = new ArrayList<>();
        List<String> avisos = new ArrayList<>();
        String plan = null;
        for (Map.Entry<String, Integer> en : cantidades.entrySet()) {
            CarritoService.Elegible e = cat.get(en.getKey());
            int cantidad = en.getValue();
            if (e == null) {
                avisos.add("Un producto del ticket ya no está a la venta.");
                continue;
            }
            String titulo = e.producto().getNombre();
            if (cantidad < 1 || cantidad > CarritoService.MAX_POR_PARTIDA) {
                throw new TiendaException(HttpStatus.BAD_REQUEST, "De «" + titulo + "» se venden de 1 a " + CarritoService.MAX_POR_PARTIDA + " piezas.");
            }
            Optional<PlanPagado> duracion = Optional.empty();
            if (e.producto().esPlan()) {
                if (cantidad > 1) throw new TiendaException(HttpStatus.BAD_REQUEST, "Los planes se venden de uno en uno.");
                duracion = CatalogoService.plan(e.producto());
                if (duracion.isPresent()) {
                    if (plan != null) {
                        throw new TiendaException(HttpStatus.BAD_REQUEST, "Solo puede ir un plan por venta: «" + plan + "» y «" + titulo + "».");
                    }
                    plan = titulo;
                }
            }
            Integer disponible = CarritoService.disponible(e.variante());
            if (disponible != null && cantidad > disponible) {
                avisos.add(disponible <= 0 ? "«" + titulo + "» se agotó." : "Solo quedan " + disponible + " de «" + titulo + "».");
                continue;
            }
            lineas.add(CarritoService.linea(e, cantidad, duracion.orElse(null)));
        }
        if (!avisos.isEmpty()) {
            throw new TiendaException(HttpStatus.CONFLICT, "El ticket cambió. Revísalo antes de cobrar.", Map.of("avisos", avisos));
        }
        double total = PedidoService.centavos(lineas.stream().mapToDouble(l -> l.precioUnitario() * l.cantidad()).sum());
        if (totalVisto == null || Math.abs(total - totalVisto) > 0.009) {
            throw new TiendaException(HttpStatus.CONFLICT, "El total cambió a " + CarritoService.dinero(total) + ". Revisa el ticket antes de cobrar.");
        }
        return lineas;
    }
}
