package com.gymtrack.repository;

import com.gymtrack.model.User;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends MongoRepository<User, String> {

    // El correo se busca sin importar mayúsculas: "Ana@Gmail.com" y
    // "ana@gmail.com" son la misma cuenta al entrar, al recuperar la contraseña
    // y al revisar si ya está registrado (el teclado del teléfono suele poner
    // la primera letra en mayúscula). Primero se busca tal cual, que es lo que
    // usa el índice; si no aparece, sin distinguir mayúsculas.
    default Optional<User> findByEmail(String email) {
        if (email == null || email.isBlank()) return Optional.empty();
        String correo = email.trim();
        return findFirstByEmail(correo).or(() -> findFirstByEmailIgnoreCase(correo));
    }

    Optional<User> findFirstByEmail(String email);
    Optional<User> findFirstByEmailIgnoreCase(String email);
    List<User> findByGymId(String gymId);
    // Miembros con fecha de corte puesta: los que revisa el cobrador automático.
    List<User> findByRoleAndFechaProximoPagoNotNull(String role);
}
