package com.gymtrack.controller;

import com.gymtrack.model.Machine;
import com.gymtrack.model.Routine;
import com.gymtrack.model.User;
import com.gymtrack.repository.MachineRepository;
import com.gymtrack.repository.RoutineRepository;
import com.gymtrack.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
public class RoutineController {

    private final RoutineRepository routineRepository;
    private final UserRepository userRepository;
    private final MachineRepository machineRepository;

    static final int MAX_EJERCICIOS = 30;

    public RoutineController(RoutineRepository routineRepository, UserRepository userRepository,
                             MachineRepository machineRepository) {
        this.routineRepository = routineRepository;
        this.userRepository = userRepository;
        this.machineRepository = machineRepository;
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
        String error = normalizarItems(gymId, routine);
        if (error != null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", error));
        }
        routine.setId(null);
        routine.setGymId(gymId);
        return ResponseEntity.ok(routineRepository.save(routine));
    }

    // PUT /api/routines/{id} → Editar rutina
    @PutMapping("/api/routines/{id}")
    public ResponseEntity<?> editarRutina(@PathVariable String id, @RequestBody Routine routine) {
        return routineRepository.findById(id).<ResponseEntity<?>>map(existing -> {
            String error = normalizarItems(existing.getGymId(), routine);
            if (error != null) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", error));
            }
            existing.setItems(routine.getItems());
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

    // Revisa los ejercicios elegidos y completa lo que sale de la máquina
    // (nombre, músculo, nombre de la máquina) con los datos del gimnasio, sin
    // confiar en lo que mande el navegador. Con items, también arma el texto de
    // "ejercicios" para las versiones de la app que solo leen ese texto.
    // Devuelve el mensaje de error, o null si todo está bien.
    private String normalizarItems(String gymId, Routine routine) {
        List<Routine.RoutineItem> items = routine.getItems();
        if (items == null || items.isEmpty()) {
            routine.setItems(new ArrayList<>());
            return null;
        }
        if (items.size() > MAX_EJERCICIOS) {
            return "Una rutina puede tener hasta " + MAX_EJERCICIOS + " ejercicios.";
        }
        List<Machine> maquinas = machineRepository.findByGymId(gymId);
        List<String> textos = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            Routine.RoutineItem it = items.get(i);
            String donde = "El ejercicio " + (i + 1);
            if (it == null) return donde + " está vacío.";
            Machine.Exercise deMaquina = null;
            if (it.getMachineId() != null && !it.getMachineId().isBlank()) {
                Machine maquina = maquinas.stream()
                        .filter(m -> it.getMachineId().equals(m.getId()))
                        .findFirst().orElse(null);
                if (maquina == null) return donde + " es de una máquina que ya no existe en tu gimnasio.";
                List<Machine.Exercise> deLaMaquina = maquina.getEjercicios() == null ? List.of() : maquina.getEjercicios();
                deMaquina = deLaMaquina.stream()
                        .filter(e -> e.getId() != null && e.getId().equals(it.getExerciseId()))
                        .findFirst().orElse(null);
                if (deMaquina == null) return donde + " ya no existe en la máquina " + maquina.getNombre() + ".";
                it.setNombre(deMaquina.getNombre());
                it.setMusculo(deMaquina.getMusculo());
                it.setMachineNombre(maquina.getNombre());
            } else {
                String nombre = it.getNombre() == null ? "" : it.getNombre().trim();
                if (nombre.isEmpty()) return donde + " necesita un nombre.";
                if (nombre.length() > 80) return donde + " tiene un nombre muy largo.";
                String musculo = it.getMusculo() == null ? "" : it.getMusculo().trim();
                it.setNombre(nombre);
                it.setMusculo(musculo.isEmpty() ? "Otro" : musculo);
                it.setMachineId(null);
                it.setExerciseId(null);
                it.setMachineNombre(null);
            }
            if (it.getSeries() == null) {
                it.setSeries(deMaquina != null && deMaquina.getSeries() != null ? deMaquina.getSeries() : 3);
            }
            if (it.getSeries() < 1 || it.getSeries() > 10) return donde + " debe tener de 1 a 10 series.";
            String reps = it.getRepeticiones() == null ? "" : it.getRepeticiones().trim();
            if (reps.isEmpty()) {
                reps = deMaquina != null && deMaquina.getRepeticiones() != null && !deMaquina.getRepeticiones().isBlank()
                        ? deMaquina.getRepeticiones().trim() : "10";
            }
            if (reps.length() > 20) return donde + ": las repeticiones son muy largas (máximo 20 caracteres).";
            it.setRepeticiones(reps);
            if (it.getDescansoSegundos() == null) it.setDescansoSegundos(90);
            if (it.getDescansoSegundos() < 0 || it.getDescansoSegundos() > 600) {
                return donde + ": el descanso debe ser de 0 a 600 segundos.";
            }
            textos.add(it.getNombre() + " — " + it.getSeries() + " x " + reps);
        }
        routine.setEjercicios(textos);
        return null;
    }
}
