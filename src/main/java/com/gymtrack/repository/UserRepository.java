package com.gymtrack.repository;

import com.gymtrack.model.User;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends MongoRepository<User, String> {
    Optional<User> findByEmail(String email);
    List<User> findByGymId(String gymId);
    // Miembros con fecha de corte puesta: los que revisa el cobrador automático.
    List<User> findByRoleAndFechaProximoPagoNotNull(String role);
}
