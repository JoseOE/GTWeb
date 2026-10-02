package com.gymtrack.repository;

import com.gymtrack.model.Carrito;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface CarritoRepository extends MongoRepository<Carrito, String> {
    Optional<Carrito> findByUserIdAndGymIdAndCanal(String userId, String gymId, String canal);
}
