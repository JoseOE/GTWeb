package com.gymtrack.repository;

import com.gymtrack.model.AvisoTienda;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface AvisoTiendaRepository extends MongoRepository<AvisoTienda, String> {
}
