package com.gymtrack.repository;

import com.gymtrack.model.Lector;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface LectorRepository extends MongoRepository<Lector, String> {
    Optional<Lector> findByLlaveHash(String llaveHash);
    List<Lector> findByGymId(String gymId);
}
