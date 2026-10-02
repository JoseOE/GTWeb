package com.gymtrack.controller;

import com.gymtrack.service.AccesoService;
import com.gymtrack.service.EscaparateService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

// La tienda que ve el miembro (tienda.html, producto.html y la app).
// Exige X-User-Id de un miembro de ese gimnasio, o de su dueño como vista previa.
@RestController
@RequestMapping("/api/tienda/{gymId}/productos")
public class TiendaController {

    private final EscaparateService escaparate;
    private final AccesoService acceso;

    public TiendaController(EscaparateService escaparate, AccesoService acceso) {
        this.escaparate = escaparate;
        this.acceso = acceso;
    }

    // GET /api/tienda/{gymId}/productos?categoria=suplementos&q=whey
    // Planes y productos publicados; los planes van primero, los destacados arriba.
    @GetMapping
    public List<Map<String, Object>> listar(@PathVariable String gymId,
                                            @RequestParam(required = false) String categoria,
                                            @RequestParam(required = false) String q,
                                            @RequestHeader(value = AccesoService.ENCABEZADO, required = false) String userId) {
        acceso.exigirVerTienda(gymId, userId);
        return escaparate.productos(gymId, categoria, q);
    }

    @GetMapping("/{productoId}")
    public Map<String, Object> ver(@PathVariable String gymId, @PathVariable String productoId,
                                   @RequestHeader(value = AccesoService.ENCABEZADO, required = false) String userId) {
        acceso.exigirVerTienda(gymId, userId);
        return escaparate.producto(gymId, productoId);
    }
}
