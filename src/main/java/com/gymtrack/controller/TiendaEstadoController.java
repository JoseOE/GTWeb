package com.gymtrack.controller;

import com.gymtrack.service.TiendaGymService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/tienda")
public class TiendaEstadoController {

    private final TiendaGymService tiendas;

    public TiendaEstadoController(TiendaGymService tiendas) {
        this.tiendas = tiendas;
    }

    // GET /api/tienda/estado → {"lista": true}. La tienda vive en MongoDB junto
    // con lo demás: si Spring responde, la tienda está lista. Se conserva para
    // las versiones de la app que lo consultan al abrir.
    @GetMapping("/estado")
    public Map<String, Object> estado() {
        return Map.of("lista", true);
    }

    // GET /api/tienda/categorias → las categorías de producto (para filtros y formularios).
    @GetMapping("/categorias")
    public List<Map<String, String>> categorias() {
        return tiendas.categorias().stream()
                .map(c -> Map.of("handle", c.handle(), "nombre", c.nombre()))
                .toList();
    }
}
