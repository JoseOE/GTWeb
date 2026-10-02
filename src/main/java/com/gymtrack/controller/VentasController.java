package com.gymtrack.controller;

import com.gymtrack.service.AccesoService;
import com.gymtrack.service.TiendaException;
import com.gymtrack.service.VentasService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Map;

// Dashboard de ventas del panel: solo el dueño del gimnasio.
//
// El recibo, el reenvío del correo y "Simular pago en tienda" de Paynet ya
// tienen sus rutas (/api/recibos/... y /api/simuladores/paynet/...): la
// pestaña Ventas las usa tal cual.
@RestController
@RequestMapping("/api/gyms/{gymId}/ventas")
public class VentasController {

    private final VentasService ventas;
    private final AccesoService acceso;

    public VentasController(VentasService ventas, AccesoService acceso) {
        this.ventas = ventas;
        this.acceso = acceso;
    }

    // GET /resumen?desde=2026-09-03&hasta=2026-10-02 (por defecto, los últimos 30 días)
    // → {kpis: {hoy, mes, ticketPromedio, paynetPendientes, membresiasDelMes},
    //    periodo, porDia: [{fecha, pedidos, total}], porMetodo, porCanal, topProductos}
    @GetMapping("/resumen")
    public Map<String, Object> resumen(@PathVariable String gymId,
                                       @RequestHeader(value = AccesoService.ENCABEZADO, required = false) String userId,
                                       @RequestParam(required = false) String desde,
                                       @RequestParam(required = false) String hasta) {
        acceso.exigirDueno(gymId, userId);
        return ventas.resumen(gymId, fecha(desde), fecha(hasta));
    }

    // GET /pedidos?metodo=tarjeta|paynet|paypal|efectivo&estado=pagado|pendiente_pago|cancelado|reembolsado
    //            &canal=web|app|mostrador&desde=&hasta=&buscar=<folio, cliente o correo>&pagina=0&tamano=20
    // → {pedidos: [...], total, pagina, paginas, cobrado}
    @GetMapping("/pedidos")
    public Map<String, Object> pedidos(@PathVariable String gymId,
                                       @RequestHeader(value = AccesoService.ENCABEZADO, required = false) String userId,
                                       @RequestParam(required = false) String metodo,
                                       @RequestParam(required = false) String estado,
                                       @RequestParam(required = false) String canal,
                                       @RequestParam(required = false) String desde,
                                       @RequestParam(required = false) String hasta,
                                       @RequestParam(required = false) String buscar,
                                       @RequestParam(required = false) Integer pagina,
                                       @RequestParam(required = false) Integer tamano) {
        acceso.exigirDueno(gymId, userId);
        return ventas.pedidos(gymId, new VentasService.Filtros(metodo, estado, canal, fecha(desde), fecha(hasta), buscar),
                pagina, tamano);
    }

    // Detalle del pedido con las acciones que admite según su estado.
    @GetMapping("/pedidos/{orderId}")
    public Map<String, Object> detalle(@PathVariable String gymId, @PathVariable String orderId,
                                       @RequestHeader(value = AccesoService.ENCABEZADO, required = false) String userId) {
        acceso.exigirDueno(gymId, userId);
        return ventas.detalle(gymId, orderId);
    }

    // Reembolso simulado → el detalle actualizado, más {membresia: {miembro, ajustada, vence}}
    // si el pedido había extendido una membresía.
    @PostMapping("/pedidos/{orderId}/reembolsar")
    public Map<String, Object> reembolsar(@PathVariable String gymId, @PathVariable String orderId,
                                          @RequestHeader(value = AccesoService.ENCABEZADO, required = false) String userId) {
        acceso.exigirDueno(gymId, userId);
        return ventas.reembolsar(gymId, orderId);
    }

    @PostMapping("/pedidos/{orderId}/cancelar")
    public Map<String, Object> cancelar(@PathVariable String gymId, @PathVariable String orderId,
                                        @RequestHeader(value = AccesoService.ENCABEZADO, required = false) String userId) {
        acceso.exigirDueno(gymId, userId);
        return ventas.cancelar(gymId, orderId);
    }

    private static LocalDate fecha(String valor) {
        if (valor == null || valor.isBlank()) return null;
        try {
            return LocalDate.parse(valor.trim());
        } catch (DateTimeParseException e) {
            throw new TiendaException(HttpStatus.BAD_REQUEST, "Fecha no válida: usa el formato AAAA-MM-DD.");
        }
    }
}
