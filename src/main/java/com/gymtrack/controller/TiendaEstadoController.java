package com.gymtrack.controller;

import com.gymtrack.service.MedusaClient;
import com.gymtrack.service.TiendaException;
import com.gymtrack.service.TiendaGymService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/tienda")
public class TiendaEstadoController {

    private final MedusaClient medusa;
    private final TiendaGymService tiendas;

    public TiendaEstadoController(MedusaClient medusa, TiendaGymService tiendas) {
        this.medusa = medusa;
        this.tiendas = tiendas;
    }

    // GET /api/tienda/estado → {"lista": true} o 503 {"despertando": true}.
    // El panel y la tienda lo llaman al abrir: si Medusa estaba dormida en
    // Render, esta llamada la despierta mientras la persona lee la página.
    @GetMapping("/estado")
    public Map<String, Object> estado() {
        if (!medusa.configurada()) {
            throw new TiendaException(HttpStatus.SERVICE_UNAVAILABLE,
                    "La tienda no está configurada: faltan MEDUSA_URL o MEDUSA_ADMIN_TOKEN.");
        }
        if (!medusa.despierta()) throw TiendaException.despertando();
        return Map.of("lista", true);
    }

    // GET /api/tienda/categorias → las categorías de producto (para filtros y formularios).
    @GetMapping("/categorias")
    public List<Map<String, String>> categorias() {
        return tiendas.base().categorias().values().stream()
                .map(c -> Map.of("handle", c.handle(), "nombre", c.nombre()))
                .toList();
    }
}
