package com.gymtrack.controller;

import com.gymtrack.model.User;
import com.gymtrack.service.AccesoService;
import com.gymtrack.service.ResenaService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

// Reseñas de los productos (ver ResenaService).
//  - Tienda (página y app): las ven los miembros del gimnasio y su dueño; solo
//    califica quien compró el producto.
//  - Panel: el dueño revisa todas y oculta las que no deben mostrarse.
@RestController
public class ResenaController {

    private final ResenaService resenas;
    private final AccesoService acceso;

    public ResenaController(ResenaService resenas, AccesoService acceso) {
        this.resenas = resenas;
        this.acceso = acceso;
    }

    // GET /api/tienda/{gymId}/productos/{productoId}/resenas?pagina=0
    // → {calificacion: {promedio, total, porEstrellas}, resenas: [...], hayMas, mia: {puede, resena}}
    @GetMapping("/api/tienda/{gymId}/productos/{productoId}/resenas")
    public Map<String, Object> deProducto(@PathVariable String gymId, @PathVariable String productoId,
                                          @RequestParam(defaultValue = "0") int pagina,
                                          @RequestHeader(value = AccesoService.ENCABEZADO, required = false) String userId) {
        User user = acceso.exigirVerTienda(gymId, userId);
        return resenas.deProducto(user, gymId, productoId, pagina);
    }

    // PUT .../resenas/mia {"estrellas": 1-5, "comentario": "..."} → crea o edita la propia.
    @PutMapping("/api/tienda/{gymId}/productos/{productoId}/resenas/mia")
    public Map<String, Object> guardar(@PathVariable String gymId, @PathVariable String productoId,
                                       @RequestBody ResenaRequest request,
                                       @RequestHeader(value = AccesoService.ENCABEZADO, required = false) String userId) {
        User user = acceso.exigirVerTienda(gymId, userId);
        return resenas.guardar(user, gymId, productoId, request.getEstrellas(), request.getComentario());
    }

    @DeleteMapping("/api/tienda/{gymId}/productos/{productoId}/resenas/mia")
    public Map<String, Object> borrar(@PathVariable String gymId, @PathVariable String productoId,
                                      @RequestHeader(value = AccesoService.ENCABEZADO, required = false) String userId) {
        User user = acceso.exigirVerTienda(gymId, userId);
        return resenas.borrar(user, gymId, productoId);
    }

    // GET /api/tienda/{gymId}/resenas/destacadas → carrusel: productos con más de 3.8 estrellas.
    @GetMapping("/api/tienda/{gymId}/resenas/destacadas")
    public List<Map<String, Object>> destacadas(@PathVariable String gymId,
                                                @RequestHeader(value = AccesoService.ENCABEZADO, required = false) String userId) {
        acceso.exigirVerTienda(gymId, userId);
        return resenas.destacados(gymId);
    }

    // ─── Panel del dueño ───

    @GetMapping("/api/gyms/{gymId}/resenas")
    public List<Map<String, Object>> delGimnasio(@PathVariable String gymId,
                                                 @RequestHeader(value = AccesoService.ENCABEZADO, required = false) String userId) {
        acceso.exigirDueno(gymId, userId);
        return resenas.delGimnasio(gymId);
    }

    // POST /api/gyms/{gymId}/resenas/{id}/visibilidad {"oculta": true|false}
    @PostMapping("/api/gyms/{gymId}/resenas/{resenaId}/visibilidad")
    public Map<String, Object> visibilidad(@PathVariable String gymId, @PathVariable String resenaId,
                                           @RequestBody Map<String, Boolean> cuerpo,
                                           @RequestHeader(value = AccesoService.ENCABEZADO, required = false) String userId) {
        acceso.exigirDueno(gymId, userId);
        return resenas.cambiarVisibilidad(gymId, resenaId, Boolean.TRUE.equals(cuerpo.get("oculta")));
    }

    public static class ResenaRequest {
        private Integer estrellas;
        private String comentario;

        public Integer getEstrellas() { return estrellas; }
        public void setEstrellas(Integer estrellas) { this.estrellas = estrellas; }

        public String getComentario() { return comentario; }
        public void setComentario(String comentario) { this.comentario = comentario; }
    }
}
