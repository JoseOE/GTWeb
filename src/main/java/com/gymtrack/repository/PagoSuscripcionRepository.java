package com.gymtrack.repository;

import com.gymtrack.model.PagoSuscripcion;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.Instant;
import java.util.List;

public interface PagoSuscripcionRepository extends MongoRepository<PagoSuscripcion, String> {
    List<PagoSuscripcion> findByGymIdOrderByPagadoEnDesc(String gymId);
    List<PagoSuscripcion> findTop50ByOrderByPagadoEnDesc();
    List<PagoSuscripcion> findByPagadoEnGreaterThanEqual(Instant desde);
}
