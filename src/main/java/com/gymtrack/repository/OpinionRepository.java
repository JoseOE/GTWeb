package com.gymtrack.repository;

import com.gymtrack.model.Opinion;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface OpinionRepository extends MongoRepository<Opinion, String> {
    Optional<Opinion> findByUserId(String userId);
    // Las ocultas no salen (oculta != true: las de antes no tienen el campo).
    List<Opinion> findTop40ByEstrellasGreaterThanEqualAndOcultaNotOrderByActualizadaEnDesc(int estrellas, boolean oculta);
    void deleteByUserId(String userId);
}
