package com.gymtrack.controller;

import com.gymtrack.model.Routine;
import com.gymtrack.model.User;
import com.gymtrack.repository.RoutineRepository;
import com.gymtrack.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
public class RoutineController {

    private final RoutineRepository routineRepository;
    private final UserRepository userRepository;

    public RoutineController(RoutineRepository routineRepository, UserRepository userRepository) {
        this.routineRepository = routineRepository;
        this.userRepository = userRepository;
    }

    // GET /api/gyms/{gymId}/routines → Rutinas de un gimnasio (panel web del dueño)
    @GetMapping("/api/gyms/{gymId}/routines")
    public List<Routine> listarRutinas(@PathVariable String gymId) {
        return routineRepository.findByGymId(gymId);
    }

    // GET /api/users/{userId}/routines → Rutinas que le tocan a este usuario.
    // Es el endpoint que usa la app: el servidor decide si tiene acceso, en vez de
    // confiar en que el cliente se autobloquee. Sin membresía activa no hay datos.
    @GetMapping("/api/users/{userId}/routines")
    public ResponseEntity<?> rutinasDelUsuario(@PathVariable String userId) {
        Optional<User> userOpt = userRepository.findById(userId);
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Usuario no encontrado."));
        }
        User user = userOpt.get();
        if (!user.tieneAccesoAlGimnasio()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Tu membresía no está activa en este gimnasio."));
        }
        return ResponseEntity.ok(routineRepository.findByGymId(user.getGymId()));
    }

    // POST /api/gyms/{gymId}/routines → Crear rutina
    @PostMapping("/api/gyms/{gymId}/routines")
    public ResponseEntity<?> crearRutina(@PathVariable String gymId, @RequestBody Routine routine) {
        if (routine.getNombre() == null || routine.getNombre().isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "La rutina necesita un nombre."));
        }
        routine.setId(null);
        routine.setGymId(gymId);
        return ResponseEntity.ok(routineRepository.save(routine));
    }

    // PUT /api/routines/{id} → Editar rutina
    @PutMapping("/api/routines/{id}")
    public ResponseEntity<?> editarRutina(@PathVariable String id, @RequestBody Routine routine) {
        return routineRepository.findById(id).<ResponseEntity<?>>map(existing -> {
            existing.setNombre(routine.getNombre());
            existing.setCategoria(routine.getCategoria());
            existing.setDescripcion(routine.getDescripcion());
            existing.setDuracion(routine.getDuracion());
            existing.setEjercicios(routine.getEjercicios());
            existing.setDia(routine.getDia());
            existing.setCoach(routine.getCoach());
            existing.setNivel(routine.getNivel());
            return ResponseEntity.ok(routineRepository.save(existing));
        }).orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Rutina no encontrada.")));
    }

    // DELETE /api/routines/{id} → Eliminar rutina
    @DeleteMapping("/api/routines/{id}")
    public ResponseEntity<?> eliminarRutina(@PathVariable String id) {
        if (!routineRepository.existsById(id)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Rutina no encontrada."));
        }
        routineRepository.deleteById(id);
        return ResponseEntity.ok(Map.of("message", "Rutina eliminada."));
    }
}
