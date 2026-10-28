package com.gymtrack.controller;

import com.gymtrack.service.AccesoService;
import com.gymtrack.service.BitacoraService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

// Bitácora de seguridad del gimnasio, para el panel del dueño (ver BitacoraService).
@RestController
@RequestMapping("/api/gyms/{gymId}/bitacora")
public class BitacoraController {

    private final BitacoraService bitacora;
    private final AccesoService acceso;

    public BitacoraController(BitacoraService bitacora, AccesoService acceso) {
        this.bitacora = bitacora;
        this.acceso = acceso;
    }

    // GET ?limite=100 → [{fecha, tipo, descripcion, propio, email, metodo, ruta, estado, ip, agente}],
    // lo más reciente primero: lo que pasó en el gimnasio y en la cuenta del dueño.
    @GetMapping
    public List<Map<String, Object>> ver(@PathVariable String gymId,
                                         @RequestHeader(value = AccesoService.ENCABEZADO, required = false) String userId,
                                         @RequestParam(defaultValue = "100") int limite) {
        acceso.exigirDueno(gymId, userId);
        return bitacora.delGimnasio(gymId, userId, limite);
    }
}
