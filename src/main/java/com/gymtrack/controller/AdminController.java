package com.gymtrack.controller;

import com.gymtrack.service.AccesoService;
import com.gymtrack.service.AdministradorService;
import com.gymtrack.service.TiendaException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

// Panel de administración del equipo de GymTrack (admin.html). Todo exige la
// sesión de la cuenta de administrador: SesionFilter lo revisa y aquí se revisa
// otra vez. Ver AdministradorService.
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AdministradorService admin;
    private final AccesoService acceso;

    public AdminController(AdministradorService admin, AccesoService acceso) {
        this.admin = admin;
        this.acceso = acceso;
    }

    // GET → {gimnasios, duenos, miembros, cuentasNuevas30, pedidosPagados, ventasTotal,
    //        ventas30, pedidos30, resenas: {total, promedio, buenas, regulares, malas}, opiniones: {…}}
    @GetMapping("/resumen")
    public Map<String, Object> resumen(@RequestHeader(value = AccesoService.ENCABEZADO, required = false) String userId) {
        exigir(userId);
        return admin.resumen();
    }

    // GET → [{id, nombre, foto, dueno: {nombre, email}, miembros, pedidosPagados, ventas,
    //         resenas: {total, promedio}, opiniones: {total, promedio}, enDirectorio, creado}]
    @GetMapping("/gimnasios")
    public List<Map<String, Object>> gimnasios(@RequestHeader(value = AccesoService.ENCABEZADO, required = false) String userId) {
        exigir(userId);
        return admin.gimnasios();
    }

    // GET → [{id, nombre, email, rol, gimnasio, estado, correoVerificado, demo, creado, ultimoAcceso}]
    @GetMapping("/usuarios")
    public List<Map<String, Object>> usuarios(@RequestHeader(value = AccesoService.ENCABEZADO, required = false) String userId) {
        exigir(userId);
        return admin.usuarios();
    }

    // GET → [{tipo: "producto" | "gimnasio", id, estrellas, texto, oculta, fecha, autor, email,
    //         gimnasio, producto?, rolAutor?, enCarrusel?}]: buenas y malas, ocultas incluidas.
    @GetMapping("/resenas")
    public List<Map<String, Object>> resenas(@RequestHeader(value = AccesoService.ENCABEZADO, required = false) String userId) {
        exigir(userId);
        return admin.resenas();
    }

    // POST {oculta: true|false} → oculta o vuelve a mostrar una reseña de producto.
    @PostMapping("/resenas/{id}/visibilidad")
    public Map<String, Object> visibilidadResena(@PathVariable String id, @RequestBody Map<String, Object> cuerpo,
                                                 @RequestHeader(value = AccesoService.ENCABEZADO, required = false) String userId) {
        exigir(userId);
        return admin.ocultarResena(id, oculta(cuerpo));
    }

    // POST {oculta: true|false} → saca o regresa una opinión del carrusel de la portada.
    @PostMapping("/opiniones/{id}/visibilidad")
    public Map<String, Object> visibilidadOpinion(@PathVariable String id, @RequestBody Map<String, Object> cuerpo,
                                                  @RequestHeader(value = AccesoService.ENCABEZADO, required = false) String userId) {
        exigir(userId);
        return admin.ocultarOpinion(id, oculta(cuerpo));
    }

    // GET ?limite=500 → la bitácora de toda la plataforma, lo más reciente primero.
    @GetMapping("/bitacora")
    public List<Map<String, Object>> bitacora(@RequestHeader(value = AccesoService.ENCABEZADO, required = false) String userId,
                                              @RequestParam(defaultValue = "500") int limite) {
        exigir(userId);
        return admin.bitacora(limite);
    }

    private void exigir(String userId) {
        admin.exigirAdmin(acceso.exigirUsuario(userId));
    }

    private static boolean oculta(Map<String, Object> cuerpo) {
        Object valor = cuerpo == null ? null : cuerpo.get("oculta");
        if (!(valor instanceof Boolean b)) throw new TiendaException(HttpStatus.BAD_REQUEST, "Indica oculta: true o false.");
        return b;
    }
}
