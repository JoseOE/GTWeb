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
        List<String> yaExisten = machineRepository.findByGymId(gymId).stream()
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
