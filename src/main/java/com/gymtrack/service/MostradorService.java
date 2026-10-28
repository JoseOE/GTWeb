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
import java.util.Set;

// Venta en el mostrador del panel. El ticket se arma en el navegador, así que
// agregar y quitar productos no espera al servidor; al cobrar llega completo,
// se revisa contra lo que hoy está a la venta y se cobra de una vez con el
// mismo PedidoService que la tienda. En efectivo, con tarjeta o con PayPal
// descuenta existencias en el acto, porque el producto se entrega en
// recepción; con pago en tienda (ficha de Paynet) lo aparta hasta que se paga.
@Service
public class MostradorService {

    private final CarritoService carritos;
    private final PedidoService pedidos;
    private final PagosService pagos;

    public MostradorService(CarritoService carritos, PedidoService pedidos, PagosService pagos) {
        this.carritos = carritos;
        this.pedidos = pedidos;
        this.pagos = pagos;
    }

    // Una partida del ticket del mostrador, tal como la manda el panel.
    public record PartidaTicket(String varianteId, Integer cantidad) {}

    public record VentaMostrador(Pedido pedido, Double cambio) {}

    // Cómo se cobra en el mostrador; "tienda" es la ficha de Paynet.
    private static final Set<String> METODOS = Set.of("efectivo", "tarjeta", "paypal", "tienda");

    // Venta en el mostrador del panel: la cobra el dueño a un miembro o al
    // público en general. Efectivo, tarjeta (la página de pago de Stripe en la
    // pantalla del panel) y PayPal (su página, igual) se entregan en el acto.
    // "tienda" genera la ficha de Paynet con Openpay: el cliente paga en una
    // tienda de la red y el producto se entrega cuando Openpay confirma el pago.
    public VentaMostrador cobrar(User dueno, String gymId, User cliente, List<PartidaTicket> partidas,
                                String metodo, Double recibido, Map<String, Object> datos, Double totalVisto) {
        if (!METODOS.contains(metodo)) {
            throw new TiendaException(HttpStatus.BAD_REQUEST, "Elige cobrar en efectivo, con tarjeta, con PayPal o con pago en tienda.");
        }
        List<PedidoService.Linea> lineas = revisarTicket(carritos.catalogo(gymId), partidas, totalVisto);
        double total = PedidoService.centavos(lineas.stream().mapToDouble(l -> l.precioUnitario() * l.cantidad()).sum());
        if ("efectivo".equals(metodo) && (recibido == null || recibido + 0.009 < total)) {
            throw new TiendaException(HttpStatus.BAD_REQUEST, "El efectivo recibido no alcanza para " + CarritoService.dinero(total) + ".");
        }
        String proveedor;
        Double cambio = null;
        Map<String, Object> extras = null;
        switch (metodo) {
            case "efectivo" -> {
                proveedor = MetodosPago.EFECTIVO;
                cambio = PedidoService.centavos(recibido - total);
                extras = Map.of("recibido", recibido, "cambio", cambio);
                datos = Map.of();
            }
            case "tarjeta" -> {
                proveedor = MetodosPago.STRIPE;
                datos = pagos.consumirStripe(dueno.getId(), gymId, Pedido.CANAL_MOSTRADOR, total, datos);
            }
            case "paypal" -> {
                proveedor = MetodosPago.PAYPAL;
                datos = pagos.consumirPaypal(dueno.getId(), gymId, Pedido.CANAL_MOSTRADOR, total, datos);
            }
            default -> {
                proveedor = MetodosPago.PAYNET;
                datos = Map.of();
            }
        }
        boolean entregaInmediata = !MetodosPago.PAYNET.equals(proveedor);
        // En una venta al público el correo del pedido es el del dueño y no se
        // manda ningún aviso.
        Pedido pedido;
        try {
            pedido = pedidos.cobrar(new PedidoService.Venta(gymId, Pedido.CANAL_MOSTRADOR,
                    cliente == null ? null : cliente.getId(),
                    cliente == null ? dueno.getEmail() : cliente.getEmail(),
                    cliente == null ? "Público en general" : cliente.getNombre(),
                    dueno.getId(), lineas, proveedor, datos, entregaInmediata, extras, null));
        } catch (RuntimeException e) {
            pagos.liberar(proveedor, datos);
            throw e;
        }
        return new VentaMostrador(pedido, cambio);
    }

    // La página de pago de Stripe para cobrar el ticket con tarjeta. El ticket se
    // revisa igual que al cobrar; la sesión es del dueño y del canal mostrador.
    public Map<String, Object> sesionTarjeta(User dueno, String gymId, List<PartidaTicket> partidas, Double totalVisto,
                                             String returnUrl, String cancelUrl, String hostPropio) {
        List<PedidoService.Linea> lineas = revisarTicket(carritos.catalogo(gymId), partidas, totalVisto);
        double total = PedidoService.centavos(lineas.stream().mapToDouble(l -> l.precioUnitario() * l.cantidad()).sum());
        List<PagosService.Concepto> conceptos = lineas.stream()
                .map(l -> new PagosService.Concepto(l.titulo() + (l.variante() == null || l.variante().isBlank() || "Única".equals(l.variante()) ? "" : " · " + l.variante()),
                        l.cantidad(), l.precioUnitario()))
                .toList();
        return pagos.crearSesionStripe(dueno, gymId, Pedido.CANAL_MOSTRADOR, total, conceptos, returnUrl, cancelUrl, hostPropio);
    }

    // La orden de PayPal para cobrar el ticket: se aprueba en la página de PayPal
    // en la pantalla del panel. Es del dueño, de este gimnasio y del canal mostrador.
    public Map<String, Object> ordenPaypal(User dueno, String gymId, List<PartidaTicket> partidas, Double totalVisto,
                                           String returnUrl, String cancelUrl, String hostPropio) {
        List<PedidoService.Linea> lineas = revisarTicket(carritos.catalogo(gymId), partidas, totalVisto);
        double total = PedidoService.centavos(lineas.stream().mapToDouble(l -> l.precioUnitario() * l.cantidad()).sum());
        int articulos = lineas.stream().mapToInt(PedidoService.Linea::cantidad).sum();
        return pagos.crearOrdenPaypal(dueno, gymId, Pedido.CANAL_MOSTRADOR, total, articulos, returnUrl, cancelUrl, hostPropio);
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
