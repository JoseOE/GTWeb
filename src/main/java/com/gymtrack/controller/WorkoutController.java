package com.gymtrack.controller;

import com.gymtrack.model.User;
import com.gymtrack.model.WorkoutSession;
import com.gymtrack.repository.UserRepository;
import com.gymtrack.repository.WorkoutRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

// Entrenamientos registrados por el usuario. Son suyos: no se bloquean nunca,
// ni cuando lo dan de baja de un gimnasio ni cuando no tiene ninguno.
@RestController
@RequestMapping("/api/users/{userId}/workouts")
public class WorkoutController {

    private final WorkoutRepository workoutRepository;
    private final UserRepository userRepository;

    public WorkoutController(WorkoutRepository workoutRepository, UserRepository userRepository) {
        this.workoutRepository = workoutRepository;
        this.userRepository = userRepository;
    }

    @GetMapping
    public List<WorkoutSession> historial(@PathVariable String userId) {
        return workoutRepository.findByUserIdOrderByFechaDesc(userId);
    }

    @PostMapping
    public ResponseEntity<?> guardar(@PathVariable String userId, @RequestBody WorkoutSession session) {
        Optional<User> userOpt = userRepository.findById(userId);
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Usuario no encontrado."));
        }
        if (session.getEjercicios() == null || session.getEjercicios().isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Registra al menos un ejercicio."));
        }

        session.setId(null);
        session.setUserId(userId);
        // Se guarda dónde entrenó, pero el entrenamiento sigue siendo del usuario.
        session.setGymId(userOpt.get().getGymId());
        if (session.getFecha() == null) session.setFecha(Instant.now());
        return ResponseEntity.ok(workoutRepository.save(session));
    }

    @DeleteMapping("/{workoutId}")
    public ResponseEntity<?> eliminar(@PathVariable String userId, @PathVariable String workoutId) {
        return workoutRepository.findById(workoutId)
                .filter(w -> userId.equals(w.getUserId()))
                .<ResponseEntity<?>>map(w -> {
                    workoutRepository.delete(w);
                    return ResponseEntity.ok(Map.of("message", "Entrenamiento eliminado."));
                })
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Entrenamiento no encontrado.")));
    }

    // GET /api/users/{userId}/workouts/stats → lo que alimenta la pestaña Progreso.
    // Todo sale de entrenamientos reales; ya no hay cifras de ejemplo.
    @GetMapping("/stats")
    public Map<String, Object> estadisticas(@PathVariable String userId) {
        List<WorkoutSession> historial = workoutRepository.findByUserIdOrderByFechaDesc(userId);
        ZoneId zona = ZoneId.systemDefault();
        LocalDate hoy = LocalDate.now(zona);

        double volumenSemana = 0;
        int sesionesMes = 0;
        long seriesTotales = 0;
        Map<String, Double> volumenPorMusculo = new HashMap<>();

        for (WorkoutSession w : historial) {
            LocalDate fecha = w.getFecha().atZone(zona).toLocalDate();
            if (!fecha.isBefore(hoy.minusDays(7))) volumenSemana += w.volumenTotal();
            if (fecha.getMonth() == hoy.getMonth() && fecha.getYear() == hoy.getYear()) sesionesMes++;

            for (WorkoutSession.LoggedExercise e : w.getEjercicios()) {
                seriesTotales += e.getSeries().size();
                String musculo = e.getMusculo() == null || e.getMusculo().isBlank() ? "Otro" : e.getMusculo();
                double vol = 0;
                for (WorkoutSession.LoggedSet s : e.getSeries()) {
                    if (s.getPeso() != null && s.getRepeticiones() != null && Boolean.TRUE.equals(s.getCompletada())) {
                        vol += s.getPeso() * s.getRepeticiones();
                    }
                }
                volumenPorMusculo.merge(musculo, vol, Double::sum);
            }
        }

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("totalSesiones", historial.size());
        stats.put("sesionesEsteMes", sesionesMes);
        stats.put("volumenSemana", Math.round(volumenSemana));
        stats.put("seriesTotales", seriesTotales);
        stats.put("rachaDias", calcularRacha(historial, zona, hoy));
        stats.put("volumenPorMusculo", volumenPorMusculo);
        stats.put("ultimoEntrenamiento", historial.isEmpty() ? null : historial.get(0).getFecha());
        return stats;
    }

    // Días seguidos entrenando hacia atrás. Cuenta desde hoy o desde ayer, para no
    // romper la racha de alguien que todavía no entrena hoy.
    private long calcularRacha(List<WorkoutSession> historial, ZoneId zona, LocalDate hoy) {
        if (historial.isEmpty()) return 0;
        List<LocalDate> dias = historial.stream()
                .map(w -> w.getFecha().atZone(zona).toLocalDate())
                .distinct()
                .sorted((a, b) -> b.compareTo(a))
                .toList();

        LocalDate masReciente = dias.get(0);
        if (ChronoUnit.DAYS.between(masReciente, hoy) > 1) return 0;

        long racha = 1;
        for (int i = 1; i < dias.size(); i++) {
            if (ChronoUnit.DAYS.between(dias.get(i), dias.get(i - 1)) == 1) racha++;
            else break;
        }
        return racha;
    }
}
