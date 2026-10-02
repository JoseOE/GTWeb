package com.gymtrack.controller;

import com.gymtrack.service.AccesoService;
import com.gymtrack.service.CatalogoService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

// Productos que el dueño vende en su tienda (suplementos, bebidas, snacks,
// accesorios). Cada llamada exige el encabezado X-User-Id del dueño.
@RestController
@RequestMapping("/api/gyms/{gymId}/productos")
public class ProductoController {

    private final CatalogoService catalogo;
    private final AccesoService acceso;

    public ProductoController(CatalogoService catalogo, AccesoService acceso) {
        this.catalogo = catalogo;
        this.acceso = acceso;
    }

    @GetMapping
    public List<Map<String, Object>> listar(@PathVariable String gymId,
                                            @RequestHeader(value = AccesoService.ENCABEZADO, required = false) String userId) {
        acceso.exigirDueno(gymId, userId);
        return catalogo.listarProductos(gymId);
    }

    @GetMapping("/{productoId}")
    public Map<String, Object> obtener(@PathVariable String gymId, @PathVariable String productoId,
                                       @RequestHeader(value = AccesoService.ENCABEZADO, required = false) String userId) {
        acceso.exigirDueno(gymId, userId);
        return catalogo.obtenerProducto(gymId, productoId);
    }

    @PostMapping
    public Map<String, Object> crear(@PathVariable String gymId,
                                     @RequestHeader(value = AccesoService.ENCABEZADO, required = false) String userId,
                                     @RequestBody CatalogoService.ProductoRequest request) {
        acceso.exigirDueno(gymId, userId);
        return catalogo.crearProducto(gymId, request);
    }

    @PutMapping("/{productoId}")
    public Map<String, Object> actualizar(@PathVariable String gymId, @PathVariable String productoId,
                                          @RequestHeader(value = AccesoService.ENCABEZADO, required = false) String userId,
                                          @RequestBody CatalogoService.ProductoRequest request) {
        acceso.exigirDueno(gymId, userId);
        return catalogo.actualizarProducto(gymId, productoId, request);
    }

    @DeleteMapping("/{productoId}")
    public ResponseEntity<?> eliminar(@PathVariable String gymId, @PathVariable String productoId,
                                      @RequestHeader(value = AccesoService.ENCABEZADO, required = false) String userId) {
        acceso.exigirDueno(gymId, userId);
        catalogo.eliminarProducto(gymId, productoId);
        return ResponseEntity.ok(Map.of("message", "Producto eliminado."));
    }
}
