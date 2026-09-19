package com.gymtrack.controller;

import com.gymtrack.model.Gym;
import com.gymtrack.model.User;
import com.gymtrack.repository.GymRepository;
import com.gymtrack.repository.UserRepository;
import com.gymtrack.service.CorreoService;
import com.gymtrack.service.PushService;
import com.gymtrack.util.PasswordUtil;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

// Usuarios (miembros) de un gimnasio: el dueño los aprueba, da de alta y de baja
// desde el panel web. Un miembro llega aquí de dos maneras: o se registró en la
// app y metió el código del gimnasio (queda "pending"), o el dueño creó su cuenta
// directamente desde recepción (queda "active").
@RestController
@RequestMapping("/api/gyms/{gymId}/members")
public class MemberController {

    private static final Set<String> ESTADOS_VALIDOS =
            Set.of(User.STATUS_PENDING, User.STATUS_ACTIVE, User.STATUS_INACTIVE);

    // Las solicitudes sin resolver van arriba: son las que piden acción del dueño.
    private static final Map<String, Integer> ORDEN_ESTADO =
            Map.of(User.STATUS_PENDING, 0, User.STATUS_ACTIVE, 1, User.STATUS_INACTIVE, 2);

    private final UserRepository userRepository;
    private final GymRepository gymRepository;
    private final PushService pushService;
    private final CorreoService correoService;

    public MemberController(UserRepository userRepository, GymRepository gymRepository,
                            PushService pushService, CorreoService correoService) {
        this.userRepository = userRepository;
        this.gymRepository = gymRepository;
        this.pushService = pushService;
        this.correoService = correoService;
    }

    @GetMapping
    public List<Map<String, Object>> listarMiembros(@PathVariable String gymId) {
        return userRepository.findByGymId(gymId).stream()
                .filter(u -> "member".equals(u.getRole()))
                .sorted(Comparator
                        .<User, Integer>comparing(u -> ORDEN_ESTADO.getOrDefault(u.getMembershipStatus(), 3))
                        .thenComparing(u -> u.getNombre() == null ? "" : u.getNombre(), String.CASE_INSENSITIVE_ORDER))
                .map(this::toPublicView)
                .toList();
    }

    // POST → Alta directa desde recepción: el dueño crea la cuenta ya activa.
    @PostMapping
    public ResponseEntity<?> darDeAlta(@PathVariable String gymId, @RequestBody User request) {
        if (request.getNombre() == null || request.getNombre().isBlank()
                || request.getEmail() == null || request.getEmail().isBlank()
                || request.getPassword() == null || request.getPassword().isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Nombre, correo y contraseña son obligatorios."));
        }
        if (userRepository.findByEmail(request.getEmail()).isPresent()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Ese correo ya está registrado."));
        }

        User member = new User();
        member.setNombre(request.getNombre());
        member.setEmail(request.getEmail());
        member.setPassword(PasswordUtil.hash(request.getPassword()));
        member.setGymId(gymId);
        member.setRole("member");
        member.setMembershipStatus(User.STATUS_ACTIVE);
        userRepository.save(member);

        return ResponseEntity.ok(toPublicView(member));
    }

    // PATCH → Aprobar una solicitud, dar de baja o reactivar.
    // Acepta {"status":"active"} y, por compatibilidad, {"membershipActive":true}.
    @PatchMapping("/{userId}")
    public ResponseEntity<?> cambiarEstado(
            @PathVariable String gymId,
            @PathVariable String userId,
            @RequestBody Map<String, Object> body
    ) {
        String nuevoEstado = resolverEstado(body);
        if (nuevoEstado == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "Falta el nuevo estado (status: pending | active | inactive)."));
        }
        return userRepository.findById(userId)
                .filter(u -> gymId.equals(u.getGymId()))
                .<ResponseEntity<?>>map(u -> {
                    String estadoAnterior = u.getMembershipStatus();
                    u.setMembershipStatus(nuevoEstado);
                    userRepository.save(u);
                    notificarCambioDeEstado(u, estadoAnterior, nuevoEstado, gymId);
                    return ResponseEntity.ok(toPublicView(u));
                })
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Usuario no encontrado en este gimnasio.")));
    }

    // DELETE → Desvincular del gimnasio (rechazar una solicitud o sacar a un miembro).
    // La cuenta del usuario sobrevive: sigue usando la app sin gimnasio.
    @DeleteMapping("/{userId}")
    public ResponseEntity<?> desvincular(@PathVariable String gymId, @PathVariable String userId) {
        return userRepository.findById(userId)
                .filter(u -> gymId.equals(u.getGymId()) && "member".equals(u.getRole()))
                .<ResponseEntity<?>>map(u -> {
                    u.setGymId(null);
                    u.setMembershipStatus(User.STATUS_NONE);
                    userRepository.save(u);
                    return ResponseEntity.ok(Map.of("message", "Usuario desvinculado del gimnasio."));
                })
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Usuario no encontrado en este gimnasio.")));
    }

    // Avisa al teléfono del usuario cuando su acceso cambia. Solo en las
    // transiciones que le importan: que lo acepten y que lo den de baja.
    private void notificarCambioDeEstado(User member, String anterior, String nuevo, String gymId) {
        if (nuevo.equals(anterior)) return;
        String nombreGym = gymRepository.findById(gymId).map(Gym::getNombre).orElse("Tu gimnasio");

        if (User.STATUS_ACTIVE.equals(nuevo) && User.STATUS_PENDING.equals(anterior)) {
            pushService.enviar(member, "¡Bienvenido a " + nombreGym + "!",
                    "Tu solicitud fue aprobada. Ya puedes ver sus rutinas, máquinas y horarios.",
                    Map.of("tipo", "solicitud_aprobada", "gymId", gymId));
            correoService.miembroAprobado(member, nombreGym);
        } else if (User.STATUS_INACTIVE.equals(nuevo)) {
            pushService.enviar(member, "Tu acceso fue pausado",
                    nombreGym + " dio de baja tu membresía. Tus rutinas y tu progreso siguen disponibles.",
                    Map.of("tipo", "membresia_pausada", "gymId", gymId));
        }
    }

    private String resolverEstado(Map<String, Object> body) {
        Object status = body.get("status");
        if (status instanceof String s && ESTADOS_VALIDOS.contains(s)) return s;
        Object legacy = body.get("membershipActive");
        if (legacy instanceof Boolean b) return b ? User.STATUS_ACTIVE : User.STATUS_INACTIVE;
        return null;
    }

    // Nunca se expone el hash de la contraseña en las respuestas
    private Map<String, Object> toPublicView(User user) {
        Map<String, Object> view = new HashMap<>();
        view.put("id", user.getId());
        view.put("nombre", user.getNombre());
        view.put("email", user.getEmail());
        view.put("membershipStatus", user.getMembershipStatus());
        view.put("membershipActive", user.getMembershipActive());
        view.put("fechaProximoPago", user.getFechaProximoPago());
        view.put("diaDePago", user.getDiaDePago());
        view.put("diasParaVencer", user.diasParaVencer(LocalDate.now()));
        view.put("tienePush", user.getPushToken() != null && !user.getPushToken().isBlank());
        return view;
    }
}
