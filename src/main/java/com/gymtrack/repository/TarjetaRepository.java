package com.gymtrack.repository;

import com.gymtrack.model.Tarjeta;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface TarjetaRepository extends MongoRepository<Tarjeta, String> {
    Optional<Tarjeta> findByEpc(String epc);
    List<Tarjeta> findByGymId(String gymId);
}
