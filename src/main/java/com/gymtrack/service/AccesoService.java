package com.gymtrack.service;

import com.gymtrack.model.Gym;
import com.gymtrack.model.Pedido;
import com.gymtrack.model.User;
import com.gymtrack.repository.GymRepository;
import com.gymtrack.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

// Quién puede hacer qué en la tienda.
//
// Todavía no hay tokens: la página y la app mandan el userId que guardaron al
// iniciar sesión en el encabezado X-User-Id. No es una sesión segura, pero el
// servidor al menos comprueba que ese usuario tenga que ver con lo que pide
// (que sea el dueño del gimnasio, o el comprador del pedido) en lugar de
// confiar en el gymId o el orderId que vienen en la URL.
@Service
public class AccesoService {

    public static final String ENCABEZADO = "X-User-Id";

    private final UserRepository userRepository;
    private final GymRepository gymRepository;

    public AccesoService(UserRepository userRepository, GymRepository gymRepository) {
        this.userRepository = userRepository;
        this.gymRepository = gymRepository;
    }

    public User exigirUsuario(String userId) {
        if (userId == null || userId.isBlank()) {
            throw new TiendaException(HttpStatus.UNAUTHORIZED, "Inicia sesión para continuar.");
        }
        return userRepository.findById(userId)
                .orElseThrow(() -> new TiendaException(HttpStatus.UNAUTHORIZED, "Tu sesión ya no es válida. Inicia sesión de nuevo."));
    }

    // El panel solo puede tocar el gimnasio de quien lo administra.
    public Gym exigirDueno(String gymId, String userId) {
        User user = exigirUsuario(userId);
        Gym gym = gymRepository.findById(gymId)
                .orElseThrow(() -> new TiendaException(HttpStatus.NOT_FOUND, "Gimnasio no encontrado."));
        if (!"owner".equals(user.getRole()) || !user.getId().equals(gym.getOwnerId())) {
            throw new TiendaException(HttpStatus.FORBIDDEN, "Solo el dueño de este gimnasio puede hacer eso.");
        }
        return gym;
    }

    // Un pedido lo ve quien lo compró y el dueño del gimnasio donde se compró.
    public User exigirAccesoAPedido(Pedido pedido, String userId) {
        User user = exigirUsuario(userId);
        boolean esComprador = user.getId().equals(pedido.getUserId());
        boolean esDueno = "owner".equals(user.getRole()) && pedido.getGymId() != null
                && gymRepository.findById(pedido.getGymId()).map(g -> user.getId().equals(g.getOwnerId())).orElse(false);
        if (!esComprador && !esDueno) {
            throw new TiendaException(HttpStatus.NOT_FOUND, "Pedido no encontrado.");
        }
        return user;
    }
}
