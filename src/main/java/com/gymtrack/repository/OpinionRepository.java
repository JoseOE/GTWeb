package com.gymtrack.repository;

import com.gymtrack.model.Opinion;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface OpinionRepository extends MongoRepository<Opinion, String> {
    Optional<Opinion> findByUserId(String userId);
    List<Opinion> findTop40ByEstrellasGreaterThanEqualOrderByActualizadaEnDesc(int estrellas);
    void deleteByUserId(String userId);
}
