package com.gymtrack.controller;

import com.gymtrack.model.Gym;
import com.gymtrack.model.User;
import com.gymtrack.repository.GymRepository;
import com.gymtrack.repository.UserRepository;
import com.gymtrack.service.AccesoService;
import com.gymtrack.util.JoinCodeUtil;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

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
    // El dueño es quien tiene la sesión: el ownerUserId del cuerpo se acepta solo
    // si es el mismo (la página lo sigue mandando).
    @PostMapping
    public ResponseEntity<?> crearGimnasio(@RequestBody GymRequest request,
                                           @RequestHeader(value = AccesoService.ENCABEZADO, required = false) String sesion) {
        if (sesion == null || sesion.isBlank()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Inicia sesión para continuar."));
        }
        if (request.getOwnerUserId() != null && !request.getOwnerUserId().isBlank() && !request.getOwnerUserId().equals(sesion)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Solo puedes configurar tu propio gimnasio."));
        }
        Optional<User> ownerOpt = userRepository.findById(sesion);
        if (ownerOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Usuario no encontrado."));
        }

        User owner = ownerOpt.get();
        if (!"owner".equals(owner.getRole())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Solo una cuenta de dueño puede registrar un gimnasio."));
        }
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
        // Datos del vendedor: solo se tocan si vienen (la app no los manda).
        if (request.getRfc() != null) {
            String rfc = request.getRfc().trim().toUpperCase();
            if (!rfc.isEmpty() && !RFC.matcher(rfc).matches()) {
                return ResponseEntity.badRequest().body(Map.of("error", "El RFC no es válido. Revisa que tenga 12 o 13 caracteres, por ejemplo XAXX010101000."));
            }
            gym.setRfc(rfc.isEmpty() ? null : rfc);
        }
        if (request.getCorreoContacto() != null) {
            String correo = request.getCorreoContacto().trim().toLowerCase();
            if (!correo.isEmpty() && !CORREO.matcher(correo).matches()) {
                return ResponseEntity.badRequest().body(Map.of("error", "El correo de atención a clientes no es válido."));
            }
            gym.setCorreoContacto(correo.isEmpty() ? null : correo);
        }
        if (request.getRazonSocial() != null) {
            String razon = request.getRazonSocial().trim();
            gym.setRazonSocial(razon.isEmpty() ? null : razon);
        }
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
    // El dueño recibe todo (código de acceso incluido); cualquier otro, solo lo
    // que el gimnasio muestra al público: ni el código, ni quién es el dueño, ni
    // los datos internos de su tienda.
    @GetMapping("/{id}")
    public ResponseEntity<?> obtenerGimnasio(@PathVariable String id,
                                             @RequestHeader(value = AccesoService.ENCABEZADO, required = false) String userId) {
        return gymRepository.findById(id)
                .<ResponseEntity<?>>map(gym -> ResponseEntity.ok(userId != null && userId.equals(gym.getOwnerId()) ? gym : vistaPublica(gym)))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Gimnasio no encontrado.")));
    }

    private static Map<String, Object> vistaPublica(Gym gym) {
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("id", gym.getId());
        v.put("nombre", gym.getNombre());
        v.put("telefono", gym.getTelefono());
        v.put("direccion", gym.getDireccion());
        v.put("horario", gym.getHorario());
        v.put("equipamiento", gym.getEquipamiento());
        v.put("maquinas", gym.getMaquinas());
        v.put("logo", gym.getLogo());
        v.put("colorPrimario", gym.getColorPrimario());
        v.put("cuotaMensual", gym.getCuotaMensual());
        // Quién vende: la tienda lo muestra antes de comprar.
        v.put("razonSocial", gym.getRazonSocial());
        v.put("rfc", gym.getRfc());
        v.put("correoContacto", gym.getCorreoContacto());
        return v;
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

    // RFC de persona moral (3 letras) o física (4), fecha AAMMDD y homoclave.
    private static final Pattern RFC = Pattern.compile("^[A-ZÑ&]{3,4}\\d{6}[A-Z\\d]{3}$");
    private static final Pattern CORREO = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

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
        private String razonSocial;
        private String rfc;
        private String correoContacto;
        private String ownerUserId;

        public String getRazonSocial() { return razonSocial; }
        public void setRazonSocial(String razonSocial) { this.razonSocial = razonSocial; }

        public String getRfc() { return rfc; }
        public void setRfc(String rfc) { this.rfc = rfc; }

        public String getCorreoContacto() { return correoContacto; }
        public void setCorreoContacto(String correoContacto) { this.correoContacto = correoContacto; }

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
