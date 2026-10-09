package com.gymtrack.repository;

import com.gymtrack.model.Gym;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface GymRepository extends MongoRepository<Gym, String> {
    Optional<Gym> findByCodigo(String codigo);
    List<Gym> findByEnDirectorioTrue();
}
