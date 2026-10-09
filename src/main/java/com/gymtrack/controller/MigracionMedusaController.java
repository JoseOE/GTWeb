package com.gymtrack.controller;

import com.gymtrack.service.AccesoService;
import com.gymtrack.service.MigracionMedusaService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

// Migración de un solo uso de la tienda de Medusa a MongoDB (ver
// MigracionMedusaService). Solo el dueño del gimnasio, con sesión; la URL y la
// llave de admin de Medusa llegan en el cuerpo y no se guardan.
@RestController
@RequestMapping("/api/gyms/{gymId}/tienda/migrar-medusa")
public class MigracionMedusaController {

    private final MigracionMedusaService migracion;
    private final AccesoService acceso;

    public MigracionMedusaController(MigracionMedusaService migracion, AccesoService acceso) {
        this.migracion = migracion;
        this.acceso = acceso;
    }

    // POST {"medusaUrl": "https://...", "adminToken": "sk_..."}
    // → {productosNuevos, planesNuevos, productosYaEstaban, pedidosNuevos, pedidosCompletados, pedidosYaEstaban, avisos}
    @PostMapping
    public Map<String, Object> migrar(@PathVariable String gymId,
                                      @RequestHeader(value = AccesoService.ENCABEZADO, required = false) String userId,
                                      @RequestBody Map<String, String> datos) {
        acceso.exigirDueno(gymId, userId);
        return migracion.migrar(gymId, datos.get("medusaUrl"), datos.get("adminToken"));
    }
}
