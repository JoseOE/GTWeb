package com.gymtrack.repository;

import com.gymtrack.model.EventoSeguridad;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface EventoSeguridadRepository extends MongoRepository<EventoSeguridad, String> {
}
