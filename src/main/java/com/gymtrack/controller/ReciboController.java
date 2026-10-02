package com.gymtrack.controller;

import com.gymtrack.model.Gym;
import com.gymtrack.model.Payment;
import com.gymtrack.model.Pedido;
import com.gymtrack.model.User;
import com.gymtrack.repository.GymRepository;
import com.gymtrack.repository.PaymentRepository;
import com.gymtrack.repository.PedidoRepository;
import com.gymtrack.service.AccesoService;
import com.gymtrack.service.AvisosPedidoService;
import com.gymtrack.service.PedidoService;
import com.gymtrack.service.ReciboService;
import com.gymtrack.service.TiendaException;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Set;

// Recibos en PDF (y el código de barras Paynet en PNG). Los pide la página o
// la app con fetch y el encabezado X-User-Id: solo los ve quien compró y el
// dueño del gimnasio donde se compró.
@RestController
@RequestMapping("/api/recibos")
public class ReciboController {

    private static final Set<String> CON_RECIBO = Set.of(Pedido.ESTADO_PAGADO, Pedido.ESTADO_REEMBOLSADO);

    private final ReciboService recibos;
    private final AvisosPedidoService avisos;
    private final PedidoService pedidoService;
    private final PedidoRepository pedidoRepository;
    private final PaymentRepository paymentRepository;
    private final GymRepository gymRepository;
    private final AccesoService acceso;

    public ReciboController(ReciboService recibos, AvisosPedidoService avisos, PedidoService pedidoService,
                            PedidoRepository pedidoRepository, PaymentRepository paymentRepository,
                            GymRepository gymRepository, AccesoService acceso) {
        this.recibos = recibos;
        this.avisos = avisos;
        this.pedidoService = pedidoService;
        this.pedidoRepository = pedidoRepository;
        this.paymentRepository = paymentRepository;
        this.gymRepository = gymRepository;
        this.acceso = acceso;
    }

    // GET /api/recibos/pedidos/{orderId}.pdf → recibo tamaño carta;
    // ?formato=ticket → ticket de 80 mm para la impresora del mostrador.
    @GetMapping("/pedidos/{orderId}.pdf")
    public ResponseEntity<byte[]> reciboPedido(@PathVariable String orderId, @RequestParam(required = false) String formato,
                                               @RequestHeader(value = AccesoService.ENCABEZADO, required = false) String userId) {
        Pedido p = pedido(orderId, userId);
        if (!CON_RECIBO.contains(p.getEstado())) {
            throw new TiendaException(HttpStatus.CONFLICT, Pedido.ESTADO_PENDIENTE_PAGO.equals(p.getEstado())
                    ? "Este pedido todavía no está pagado: descarga su ficha de pago."
                    : "Este pedido se canceló y no tiene recibo.");
        }
        boolean ticket = "ticket".equals(formato);
        return pdf(ticket ? recibos.ticketPedido(p) : recibos.reciboPedido(p),
                (ticket ? "ticket-" : "recibo-") + p.getFolio() + ".pdf");
    }

    // GET /api/recibos/paynet/{orderId}.pdf → ficha para pagar en tienda.
    @GetMapping("/paynet/{orderId}.pdf")
    public ResponseEntity<byte[]> fichaPaynet(@PathVariable String orderId,
                                              @RequestHeader(value = AccesoService.ENCABEZADO, required = false) String userId) {
        Pedido p = pedido(orderId, userId);
        return pdf(recibos.fichaPaynet(p), "ficha-paynet-" + p.getFolio() + ".pdf");
    }

    // GET /api/recibos/paynet/{orderId}/codigo.png → código de barras de la referencia (web y app).
    @GetMapping("/paynet/{orderId}/codigo.png")
    public ResponseEntity<byte[]> codigoPaynet(@PathVariable String orderId,
                                               @RequestHeader(value = AccesoService.ENCABEZADO, required = false) String userId) {
        Pedido p = pedido(orderId, userId);
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .cacheControl(CacheControl.noStore())
                .body(recibos.codigoPaynetPng(p));
    }

    // GET /api/recibos/pagos/{paymentId}.pdf → recibo de una mensualidad registrada en el panel.
    @GetMapping("/pagos/{paymentId}.pdf")
    public ResponseEntity<byte[]> reciboPago(@PathVariable String paymentId,
                                             @RequestHeader(value = AccesoService.ENCABEZADO, required = false) String userId) {
        Payment pago = pago(paymentId, userId);
        return pdf(recibos.reciboPago(pago), "recibo-membresia-" + pago.getFechaPago() + ".pdf");
    }

    // POST /api/recibos/pedidos/{orderId}/enviar → reenvía el recibo (o la ficha Paynet) al correo del comprador.
    @PostMapping("/pedidos/{orderId}/enviar")
    public Map<String, Object> reenviarPedido(@PathVariable String orderId,
                                              @RequestHeader(value = AccesoService.ENCABEZADO, required = false) String userId) {
        Pedido p = pedido(orderId, userId);
        return respuestaEnvio(avisos.reenviarPedido(p, pedidoService.vista(p)));
    }

    // POST /api/recibos/pagos/{paymentId}/enviar → reenvía el recibo de una mensualidad al miembro.
    @PostMapping("/pagos/{paymentId}/enviar")
    public Map<String, Object> reenviarPago(@PathVariable String paymentId,
                                            @RequestHeader(value = AccesoService.ENCABEZADO, required = false) String userId) {
        return respuestaEnvio(avisos.enviarReciboDePago(pago(paymentId, userId)));
    }

    private Pedido pedido(String orderId, String userId) {
        acceso.exigirUsuario(userId);
        Pedido p = pedidoRepository.findByOrderId(orderId).orElseGet(() -> {
            try {
                return pedidoService.sincronizar(orderId);
            } catch (TiendaException e) {
                if (e.getStatus() == HttpStatus.NOT_FOUND) throw new TiendaException(HttpStatus.NOT_FOUND, "Pedido no encontrado.");
                throw e;
            }
        });
        acceso.exigirAccesoAPedido(p, userId);
        return p;
    }

    // Un recibo de mensualidad lo ve el miembro que pagó y el dueño de ese gimnasio.
    private Payment pago(String paymentId, String userId) {
        User user = acceso.exigirUsuario(userId);
        Payment pago = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new TiendaException(HttpStatus.NOT_FOUND, "Pago no encontrado."));
        boolean esMiembro = user.getId().equals(pago.getUserId());
        boolean esDueno = "owner".equals(user.getRole()) && pago.getGymId() != null
                && gymRepository.findById(pago.getGymId()).map(Gym::getOwnerId).map(user.getId()::equals).orElse(false);
        if (!esMiembro && !esDueno) throw new TiendaException(HttpStatus.NOT_FOUND, "Pago no encontrado.");
        return pago;
    }

    // inline: el navegador lo abre en su visor; la página decide si lo descarga.
    private static ResponseEntity<byte[]> pdf(byte[] contenido, String nombre) {
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline().filename(nombre).build().toString())
                .cacheControl(CacheControl.noStore())
                .body(contenido);
    }

    private static Map<String, Object> respuestaEnvio(boolean enviado) {
        return enviado
                ? Map.of("enviado", true, "message", "Se envió al correo del comprador.")
                : Map.of("enviado", false, "message", "No se pudo enviar el correo en este momento. Descárgalo desde aquí.");
    }
}
