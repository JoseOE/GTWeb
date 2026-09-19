package com.gymtrack.controller;

import com.gymtrack.model.Gym;
import com.gymtrack.model.User;
import com.gymtrack.repository.GymRepository;
import com.gymtrack.repository.UserRepository;
import com.gymtrack.util.JoinCodeUtil;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/gyms")
public class GymController {

    private final GymRepository gymRepository;
    private final UserRepository userRepository;

    public GymController(GymRepository gymRepository, UserRepository userRepository) {
        this.gymRepository = gymRepository;
        this.userRepository = userRepository;
    }

    // POST /api/gyms → Crea el gimnasio y lo vincula al usuario que lo administra
    @PostMapping
    public ResponseEntity<?> crearGimnasio(@RequestBody GymRequest request) {
        if (request.getOwnerUserId() == null || request.getOwnerUserId().isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Falta el usuario dueño del gimnasio."));
        }
        Optional<User> ownerOpt = userRepository.findById(request.getOwnerUserId());
        if (ownerOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Usuario no encontrado."));
        }

        User owner = ownerOpt.get();
        Gym gym;
        if (owner.getGymId() != null && gymRepository.existsById(owner.getGymId())) {
            // El usuario ya tiene un gimnasio: se actualiza en lugar de duplicarlo
            gym = gymRepository.findById(owner.getGymId()).orElseThrow();
        } else {
            gym = new Gym();
        }
        gym.setNombre(request.getNombre());
        gym.setTelefono(request.getTelefono());
        gym.setDireccion(request.getDireccion());
        gym.setHorario(request.getHorario());
        gym.setEquipamiento(request.getEquipamiento());
        gym.setMaquinas(request.getMaquinas() == null ? new ArrayList<>() : request.getMaquinas());
        gym.setLogo(request.getLogo());
        gym.setColorPrimario(request.getColorPrimario());
        gym.setEnDirectorio(Boolean.TRUE.equals(request.getEnDirectorio()));
        gym.setCuotaMensual(request.getCuotaMensual());
        gym.setOwnerId(owner.getId());
        if (gym.getCodigo() == null || gym.getCodigo().isBlank()) {
            gym.setCodigo(generarCodigoUnico());
        }
        gym = gymRepository.save(gym);

        owner.setGymId(gym.getId());
        userRepository.save(owner);

        return ResponseEntity.ok(gym);
    }

    // GET /api/gyms/lookup?codigo=PWR4-K7M2 → vista previa pública del gimnasio.
    // La app la usa para que el usuario confirme que es su gimnasio antes de unirse.
    @GetMapping("/lookup")
    public ResponseEntity<?> buscarPorCodigo(@RequestParam String codigo) {
        String normalizado = JoinCodeUtil.normalize(codigo);
        return gymRepository.findByCodigo(normalizado)
                .<ResponseEntity<?>>map(gym -> ResponseEntity.ok(Map.of(
                        "id", gym.getId(),
                        "nombre", gym.getNombre() == null ? "" : gym.getNombre(),
                        "direccion", gym.getDireccion() == null ? "" : gym.getDireccion()
                )))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "No encontramos ningún gimnasio con ese código.")));
    }

    // GET /api/gyms/directory → gimnasios que aceptaron aparecer en la app.
    // Solo datos públicos: nunca el código de acceso ni el teléfono del dueño.
    @GetMapping("/directory")
    public List<Map<String, Object>> directorio() {
        return gymRepository.findByEnDirectorioTrue().stream()
                .map(gym -> {
                    Map<String, Object> view = new LinkedHashMap<>();
                    view.put("id", gym.getId());
                    view.put("nombre", gym.getNombre() == null ? "" : gym.getNombre());
                    view.put("direccion", gym.getDireccion() == null ? "" : gym.getDireccion());
                    view.put("horario", gym.getHorario() == null ? "" : gym.getHorario());
                    view.put("logo", gym.getLogo());
                    view.put("colorPrimario", gym.getColorPrimario());
                    return view;
                })
                .toList();
    }

    // GET /api/gyms/{id} → Obtener un gimnasio por ID
    @GetMapping("/{id}")
    public ResponseEntity<?> obtenerGimnasio(@PathVariable String id) {
        return gymRepository.findById(id)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Gimnasio no encontrado.")));
    }

    // POST /api/gyms/{id}/codigo → genera un código nuevo e invalida el anterior
    // (por si se filtró y hay gente entrando sin permiso).
    @PostMapping("/{id}/codigo")
    public ResponseEntity<?> regenerarCodigo(@PathVariable String id) {
        return gymRepository.findById(id)
                .<ResponseEntity<?>>map(gym -> {
                    gym.setCodigo(generarCodigoUnico());
                    gymRepository.save(gym);
                    return ResponseEntity.ok(Map.of("codigo", gym.getCodigo()));
                })
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Gimnasio no encontrado.")));
    }

    private String generarCodigoUnico() {
        for (int intento = 0; intento < 10; intento++) {
            String candidato = JoinCodeUtil.generate();
            if (gymRepository.findByCodigo(candidato).isEmpty()) return candidato;
        }
        throw new IllegalStateException("No se pudo generar un código de gimnasio único.");
    }

    public static class GymRequest {
        private String nombre;
        private String telefono;
        private String direccion;
        private String horario;
        private String equipamiento;
        private List<String> maquinas;
        private String logo;
        private String colorPrimario;
        private Boolean enDirectorio;
        private Double cuotaMensual;
        private String ownerUserId;

        public String getLogo() { return logo; }
        public void setLogo(String logo) { this.logo = logo; }

        public String getColorPrimario() { return colorPrimario; }
        public void setColorPrimario(String colorPrimario) { this.colorPrimario = colorPrimario; }

        public Boolean getEnDirectorio() { return enDirectorio; }
        public void setEnDirectorio(Boolean enDirectorio) { this.enDirectorio = enDirectorio; }

        public Double getCuotaMensual() { return cuotaMensual; }
        public void setCuotaMensual(Double cuotaMensual) { this.cuotaMensual = cuotaMensual; }

        public String getNombre() { return nombre; }
        public void setNombre(String nombre) { this.nombre = nombre; }

        public String getTelefono() { return telefono; }
        public void setTelefono(String telefono) { this.telefono = telefono; }

        public String getDireccion() { return direccion; }
        public void setDireccion(String direccion) { this.direccion = direccion; }

        public String getHorario() { return horario; }
        public void setHorario(String horario) { this.horario = horario; }

        public String getEquipamiento() { return equipamiento; }
        public void setEquipamiento(String equipamiento) { this.equipamiento = equipamiento; }

        public List<String> getMaquinas() { return maquinas; }
        public void setMaquinas(List<String> maquinas) { this.maquinas = maquinas; }

        public String getOwnerUserId() { return ownerUserId; }
        public void setOwnerUserId(String ownerUserId) { this.ownerUserId = ownerUserId; }
    }
}
