package com.gymtrack.controller;

import com.gymtrack.model.Gym;
import com.gymtrack.model.User;
import com.gymtrack.repository.GymRepository;
import com.gymtrack.repository.UserRepository;
import com.gymtrack.service.CorreoService;
import com.gymtrack.service.CuentaService;
import com.gymtrack.util.PasswordUtil;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserRepository userRepository;
    private final GymRepository gymRepository;
    private final CuentaService cuentaService;
    private final CorreoService correoService;

    public UserController(UserRepository userRepository, GymRepository gymRepository,
                          CuentaService cuentaService, CorreoService correoService) {
        this.userRepository = userRepository;
        this.gymRepository = gymRepository;
        this.cuentaService = cuentaService;
        this.correoService = correoService;
    }

    @PostMapping("/register")
    public ResponseEntity<?> registerUser(@RequestBody User user) {
        if (user.getEmail() == null || user.getEmail().isBlank()
                || user.getPassword() == null || user.getPassword().isBlank()
                || user.getNombre() == null || user.getNombre().isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Nombre, correo y contraseña son obligatorios."));
        }
        if (userRepository.findByEmail(user.getEmail()).isPresent()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "El email ya está registrado."));
        }

        // La app móvil manda role="member" (usuario que se registra por su cuenta);
        // el registro del panel web no manda nada y crea un dueño de gimnasio.
        boolean esMiembro = "member".equals(user.getRole());
        user.setRole(esMiembro ? "member" : "owner");
        user.setGymId(null);
        user.setMembershipStatus(User.STATUS_NONE);
        // Las cuentas de la página web confirman su correo antes de entrar. La app
        // todavía no tiene pantalla de verificación: sus miembros entran directo.
        user.setEmailVerificado(esMiembro ? null : false);
        user.setPassword(PasswordUtil.hash(user.getPassword()));
        userRepository.save(user);

        Map<String, Object> view = accountView(user, "Usuario registrado exitosamente");
        if (esMiembro) {
            correoService.bienvenida(user);
        } else {
            view.put("verificacionPendiente", true);
            view.put("correoEnviado", cuentaService.enviarCodigoDeVerificacion(user));
        }
        return ResponseEntity.ok(view);
    }

    @PostMapping("/login")
    public ResponseEntity<?> loginUser(@RequestBody User loginRequest) {
        Optional<User> userOpt = userRepository.findByEmail(loginRequest.getEmail());

        if (userOpt.isPresent()) {
            User user = userOpt.get();
            if (PasswordUtil.coincide(loginRequest.getPassword(), user.getPassword())) {
                // Las contraseñas guardadas con el SHA-256 anterior se pasan a BCrypt al entrar.
                if (PasswordUtil.necesitaActualizarse(user.getPassword())) {
                    user.setPassword(PasswordUtil.hash(loginRequest.getPassword()));
                    userRepository.save(user);
                }
                if (user.correoPendienteDeVerificar()) {
                    Map<String, Object> pendiente = new HashMap<>();
                    pendiente.put("error", "Verifica tu correo antes de iniciar sesión.");
                    pendiente.put("verificacionPendiente", true);
                    pendiente.put("email", user.getEmail());
                    return ResponseEntity.status(HttpStatus.FORBIDDEN).body(pendiente);
                }
                return ResponseEntity.ok(accountView(user, "Login exitoso"));
            }
        }
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Credenciales incorrectas."));
    }

    // PUT /api/users/{id}/push-token → la app registra aquí el token de Expo del
    // teléfono para poder recibir avisos de aprobación y de vencimiento de pago.
    @PutMapping("/{id}/push-token")
    public ResponseEntity<?> guardarPushToken(@PathVariable String id, @RequestBody Map<String, String> body) {
        return userRepository.findById(id)
                .<ResponseEntity<?>>map(user -> {
                    user.setPushToken(body.get("pushToken"));
                    userRepository.save(user);
                    return ResponseEntity.ok(Map.of("message", "Token registrado."));
                })
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Usuario no encontrado.")));
    }

    // GET /api/users/{id}/me → estado vigente de la cuenta y su gimnasio.
    // La app lo consulta cada vez que una pestaña gana foco, así que un alta o
    // una baja hecha desde el panel web se refleja sin volver a iniciar sesión.
    @GetMapping("/{id}/me")
    public ResponseEntity<?> obtenerCuenta(@PathVariable String id) {
        return userRepository.findById(id)
                .<ResponseEntity<?>>map(user -> ResponseEntity.ok(accountView(user, null)))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Usuario no encontrado.")));
    }

    // Respuesta única que comparten register, login y /me: la app siempre recibe
    // la misma forma y no tiene que combinar datos de varias llamadas.
    private Map<String, Object> accountView(User user, String message) {
        Map<String, Object> view = new HashMap<>();
        if (message != null) view.put("message", message);
        view.put("id", user.getId());
        view.put("nombre", user.getNombre());
        view.put("email", user.getEmail());
        view.put("role", user.getRole());
        view.put("gymId", user.getGymId());
        view.put("membershipStatus", user.getMembershipStatus());
        view.put("membershipActive", user.getMembershipActive());
        view.put("emailVerificado", user.getEmailVerificado());
        view.put("hasGymAccess", user.tieneAccesoAlGimnasio());
        // Datos de cobranza: la app los usa para avisar "te quedan N días".
        view.put("fechaProximoPago", user.getFechaProximoPago());
        view.put("diaDePago", user.getDiaDePago());
        view.put("diasParaVencer", user.diasParaVencer(LocalDate.now()));

        Map<String, Object> gymView = null;
        if (user.getGymId() != null && !user.getGymId().isBlank()) {
            Gym gym = gymRepository.findById(user.getGymId()).orElse(null);
            if (gym != null) {
                gymView = new HashMap<>();
                gymView.put("id", gym.getId());
                gymView.put("nombre", gym.getNombre());
                // El branding viaja siempre que haya gimnasio vinculado, incluso
                // en pendiente o dado de baja: esas pantallas también lo usan.
                gymView.put("logo", gym.getLogo());
                gymView.put("colorPrimario", gym.getColorPrimario());
                if (user.tieneAccesoAlGimnasio()) {
                    // Los datos completos solo viajan si la membresía está activa:
                    // un usuario dado de baja deja de recibirlos, no solo de verlos.
                    gymView.put("telefono", gym.getTelefono());
                    gymView.put("direccion", gym.getDireccion());
                    gymView.put("horario", gym.getHorario());
                    gymView.put("equipamiento", gym.getEquipamiento());
                    gymView.put("maquinas", gym.getMaquinas());
                    gymView.put("cuotaMensual", gym.getCuotaMensual());
                    if ("owner".equals(user.getRole())) gymView.put("codigo", gym.getCodigo());
                }
            }
        }
        view.put("gym", gymView);
        return view;
    }
}
