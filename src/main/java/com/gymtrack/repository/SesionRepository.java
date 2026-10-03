package com.gymtrack.repository;

import com.gymtrack.model.Sesion;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface SesionRepository extends MongoRepository<Sesion, String> {
    List<Sesion> findByUserId(String userId);
}
