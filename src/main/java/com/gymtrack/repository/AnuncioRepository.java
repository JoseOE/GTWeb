package com.gymtrack.repository;

import com.gymtrack.model.Anuncio;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface AnuncioRepository extends MongoRepository<Anuncio, String> {
    List<Anuncio> findByGymIdOrderByDesdeDescCreadoEnDesc(String gymId);
    Optional<Anuncio> findByIdAndGymId(String id, String gymId);
}
