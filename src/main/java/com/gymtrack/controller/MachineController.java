package com.gymtrack.controller;

import com.gymtrack.model.Gym;
import com.gymtrack.model.Machine;
import com.gymtrack.model.User;
import com.gymtrack.repository.GymRepository;
import com.gymtrack.repository.MachineRepository;
import com.gymtrack.repository.UserRepository;
import com.gymtrack.util.MachineCatalog;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

// Máquinas de un gimnasio y los ejercicios que se pueden hacer en cada una.
@RestController
public class MachineController {

    private final MachineRepository machineRepository;
    private final GymRepository gymRepository;
    private final UserRepository userRepository;

    public MachineController(MachineRepository machineRepository, GymRepository gymRepository,
                             UserRepository userRepository) {
        this.machineRepository = machineRepository;
        this.gymRepository = gymRepository;
        this.userRepository = userRepository;
    }

    // GET /api/gyms/{gymId}/machines → panel web del dueño.
    // La primera vez migra la lista de texto que el gimnasio ya tenía capturada
    // en "Mi Gimnasio", para que no pierda lo que escribió antes.
    @GetMapping("/api/gyms/{gymId}/machines")
    public List<Machine> listar(@PathVariable String gymId) {
        List<Machine> existentes = machineRepository.findByGymId(gymId);
        if (!existentes.isEmpty()) return existentes;

        Gym gym = gymRepository.findById(gymId).orElse(null);
        if (gym == null || gym.getMaquinas() == null || gym.getMaquinas().isEmpty()) return existentes;

        List<Machine> migradas = new ArrayList<>();
        for (String nombre : gym.getMaquinas()) {
            Machine m = new Machine(gymId, nombre, "Sin zona");
            migradas.add(m);
        }
        return machineRepository.saveAll(migradas);
    }

    // GET /api/users/{userId}/machines → las que ve el miembro en la app.
    // Igual que con las rutinas, el servidor corta el acceso, no el cliente.
    @GetMapping("/api/users/{userId}/machines")
    public ResponseEntity<?> maquinasDelUsuario(@PathVariable String userId) {
        Optional<User> userOpt = userRepository.findById(userId);
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Usuario no encontrado."));
        }
        User user = userOpt.get();
        if (!user.tieneAccesoAlGimnasio()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Tu membresía no está activa en este gimnasio."));
        }
        return ResponseEntity.ok(listar(user.getGymId()));
    }

    @PostMapping("/api/gyms/{gymId}/machines")
    public ResponseEntity<?> crear(@PathVariable String gymId, @RequestBody Machine machine) {
        if (machine.getNombre() == null || machine.getNombre().isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "La máquina necesita un nombre."));
        }
        machine.setId(null);
        machine.setGymId(gymId);
        asignarIdsDeEjercicios(machine);
        return ResponseEntity.ok(machineRepository.save(machine));
    }

    @PutMapping("/api/machines/{id}")
    public ResponseEntity<?> editar(@PathVariable String id, @RequestBody Machine machine) {
        return machineRepository.findById(id).<ResponseEntity<?>>map(existing -> {
            existing.setNombre(machine.getNombre());
            existing.setZona(machine.getZona());
            existing.setDescripcion(machine.getDescripcion());
            existing.setCantidad(machine.getCantidad());
            existing.setEjercicios(machine.getEjercicios() == null ? new ArrayList<>() : machine.getEjercicios());
            asignarIdsDeEjercicios(existing);
            return ResponseEntity.ok(machineRepository.save(existing));
        }).orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Máquina no encontrada.")));
    }

    @DeleteMapping("/api/machines/{id}")
    public ResponseEntity<?> eliminar(@PathVariable String id) {
        if (!machineRepository.existsById(id)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Máquina no encontrada."));
        }
        machineRepository.deleteById(id);
        return ResponseEntity.ok(Map.of("message", "Máquina eliminada."));
    }

    // POST /api/gyms/{gymId}/machines/import → carga el catálogo base de un clic.
    // Solo agrega las que faltan, así se puede repetir sin duplicar nada.
    @PostMapping("/api/gyms/{gymId}/machines/import")
    public ResponseEntity<?> importarCatalogo(@PathVariable String gymId) {
        List<Machine> actuales = machineRepository.findByGymId(gymId);
        // A los ejercicios que ya tiene el gimnasio y vienen del catálogo se les
        // ponen las instrucciones, solo si no tienen.
        List<Machine> completadas = new ArrayList<>();
        for (Machine m : actuales) {
            boolean cambio = false;
            for (Machine.Exercise e : m.getEjercicios() == null ? List.<Machine.Exercise>of() : m.getEjercicios()) {
                String instrucciones = MachineCatalog.instrucciones(e.getNombre());
                if (instrucciones != null && (e.getInstrucciones() == null || e.getInstrucciones().isBlank())) {
                    e.setInstrucciones(instrucciones);
                    cambio = true;
                }
            }
            if (cambio) completadas.add(m);
        }
        machineRepository.saveAll(completadas);

        List<String> yaExisten = actuales.stream()
                .map(m -> m.getNombre() == null ? "" : m.getNombre().trim().toLowerCase())
                .toList();

        List<Machine> nuevas = MachineCatalog.plantillaPara(gymId).stream()
                .filter(m -> !yaExisten.contains(m.getNombre().trim().toLowerCase()))
                .toList();

        machineRepository.saveAll(nuevas);
        return ResponseEntity.ok(Map.of(
                "message", nuevas.size() + " máquinas agregadas desde el catálogo.",
                "agregadas", nuevas.size()
        ));
    }

    // GET /api/gyms/{gymId}/machines/catalogo → el catálogo grande para elegir
    // máquinas por categoría. Cada máquina dice si el gimnasio ya la tiene y
    // cuáles de sus ejercicios ya tiene capturados.
    @GetMapping("/api/gyms/{gymId}/machines/catalogo")
    public Map<String, Object> catalogo(@PathVariable String gymId) {
        Map<String, Machine> delGimnasio = new java.util.HashMap<>();
        for (Machine m : machineRepository.findByGymId(gymId)) {
            if (m.getNombre() != null) delGimnasio.putIfAbsent(clave(m.getNombre()), m);
        }
        List<Map<String, Object>> maquinas = new ArrayList<>();
        for (MachineCatalog.MaquinaCatalogo c : MachineCatalog.catalogo()) {
            Machine propia = delGimnasio.get(clave(c.nombre));
            List<String> tiene = propia == null || propia.getEjercicios() == null ? List.of()
                    : propia.getEjercicios().stream().map(e -> e.getNombre()).toList();
            Map<String, Object> v = new java.util.LinkedHashMap<>();
            v.put("nombre", c.nombre);
            v.put("categoria", c.categoria);
            v.put("zona", c.zona);
            v.put("descripcion", c.descripcion);
            v.put("ejercicios", c.ejercicios);
            v.put("machineId", propia == null ? null : propia.getId());
            v.put("ejerciciosQueTienes", tiene);
            maquinas.add(v);
        }
        return Map.of("categorias", MachineCatalog.CATEGORIAS, "maquinas", maquinas);
    }

    // POST /api/gyms/{gymId}/machines/catalogo {nombre, ejercicios: [nombres]}
    // → agrega esa máquina del catálogo con los ejercicios elegidos. Si el
    // gimnasio ya la tiene (mismo nombre), solo le suma los que le falten.
    @PostMapping("/api/gyms/{gymId}/machines/catalogo")
    public ResponseEntity<?> agregarDelCatalogo(@PathVariable String gymId, @RequestBody Map<String, Object> cuerpo) {
        String nombre = cuerpo.get("nombre") instanceof String s ? s : null;
        MachineCatalog.MaquinaCatalogo c = MachineCatalog.catalogo().stream()
                .filter(x -> nombre != null && clave(x.nombre).equals(clave(nombre))).findFirst().orElse(null);
        if (c == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Esa máquina no está en el catálogo."));
        }
        List<String> elegidos = cuerpo.get("ejercicios") instanceof List<?> l
                ? l.stream().filter(String.class::isInstance).map(x -> clave((String) x)).toList() : List.of();
        List<Machine.Exercise> nuevos = c.ejercicios.stream()
                .filter(e -> elegidos.contains(clave(e.getNombre()))).map(MachineCatalog::copia).toList();
        if (nuevos.isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Elige al menos un ejercicio."));
        }
        Machine m = machineRepository.findByGymId(gymId).stream()
                .filter(x -> x.getNombre() != null && clave(x.getNombre()).equals(clave(c.nombre))).findFirst()
                .orElseGet(() -> {
                    Machine n = new Machine(gymId, c.nombre, c.zona);
                    n.setDescripcion(c.descripcion);
                    return n;
                });
        if (m.getEjercicios() == null) m.setEjercicios(new ArrayList<>());
        List<String> yaTiene = m.getEjercicios().stream().map(e -> clave(e.getNombre())).toList();
        int agregados = 0;
        for (Machine.Exercise e : nuevos) {
            if (yaTiene.contains(clave(e.getNombre()))) continue;
            m.getEjercicios().add(e);
            agregados++;
        }
        Machine guardada = machineRepository.save(m);
        return ResponseEntity.ok(Map.of("maquina", guardada, "agregados", agregados));
    }

    private static String clave(String texto) {
        return texto == null ? "" : texto.trim().toLowerCase();
    }

    // Los ejercicios viven dentro del documento de la máquina, así que Mongo no
    // les pone id solo: se lo asignamos aquí para poder referenciarlos después
    // desde un entrenamiento registrado.
    private void asignarIdsDeEjercicios(Machine machine) {
        if (machine.getEjercicios() == null) return;
        for (Machine.Exercise e : machine.getEjercicios()) {
            if (e.getId() == null || e.getId().isBlank()) {
                e.setId(UUID.randomUUID().toString());
            }
        }
    }
}
