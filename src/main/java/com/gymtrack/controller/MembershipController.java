package com.gymtrack.controller;

import com.gymtrack.model.Gym;
import com.gymtrack.model.User;
import com.gymtrack.repository.GymRepository;
import com.gymtrack.repository.UserRepository;
import com.gymtrack.util.JoinCodeUtil;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

// Lado del usuario: unirse a un gimnasio con su código y salirse de él.
// El alta/baja la decide el gimnasio (MemberController); aquí solo se solicita.
@RestController
@RequestMapping("/api/users/{userId}/membership")
public class MembershipController {

    private final UserRepository userRepository;
    private final GymRepository gymRepository;

    public MembershipController(UserRepository userRepository, GymRepository gymRepository) {
        this.userRepository = userRepository;
        this.gymRepository = gymRepository;
    }

    // POST /api/users/{userId}/membership/join  { "codigo": "PWR4-K7M2" }
    @PostMapping("/join")
    public ResponseEntity<?> unirse(@PathVariable String userId, @RequestBody Map<String, String> body) {
        String codigo = JoinCodeUtil.normalize(body.get("codigo"));
        if (codigo == null || codigo.isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Escribe el código de tu gimnasio."));
        }

        Optional<User> userOpt = userRepository.findById(userId);
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Usuario no encontrado."));
        }
        User user = userOpt.get();
        if ("owner".equals(user.getRole())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Tu cuenta administra un gimnasio; no puede unirse a otro como miembro."));
        }

        Optional<Gym> gymOpt = gymRepository.findByCodigo(codigo);
        if (gymOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "No encontramos ningún gimnasio con ese código."));
        }
        Gym gym = gymOpt.get();

        String estadoActual = user.getMembershipStatus();
        if (gym.getId().equals(user.getGymId()) && User.STATUS_ACTIVE.equals(estadoActual)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Ya eres miembro activo de este gimnasio."));
        }
        if (gym.getId().equals(user.getGymId()) && User.STATUS_PENDING.equals(estadoActual)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Ya enviaste tu solicitud; el gimnasio aún no la aprueba."));
        }

        user.setGymId(gym.getId());
        user.setMembershipStatus(User.STATUS_PENDING);
        userRepository.save(user);

        Map<String, Object> res = new HashMap<>();
        res.put("message", "Solicitud enviada. El gimnasio debe aprobarla.");
        res.put("gymId", gym.getId());
        res.put("gymNombre", gym.getNombre());
        res.put("membershipStatus", user.getMembershipStatus());
        return ResponseEntity.ok(res);
    }

    // POST /api/users/{userId}/membership/leave → el usuario se desvincula por su cuenta.
    // Conserva su cuenta y sus datos personales; solo pierde el contenido del gimnasio.
    @PostMapping("/leave")
    public ResponseEntity<?> salir(@PathVariable String userId) {
        return userRepository.findById(userId)
                .<ResponseEntity<?>>map(user -> {
                    if ("owner".equals(user.getRole())) {
                        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Un dueño no puede desvincularse de su propio gimnasio."));
                    }
                    user.setGymId(null);
                    user.setMembershipStatus(User.STATUS_NONE);
                    userRepository.save(user);
                    return ResponseEntity.ok(Map.of("message", "Saliste del gimnasio.", "membershipStatus", user.getMembershipStatus()));
                })
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Usuario no encontrado.")));
    }
}
