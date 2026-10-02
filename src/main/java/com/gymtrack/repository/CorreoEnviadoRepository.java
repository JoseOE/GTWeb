package com.gymtrack.repository;

import com.gymtrack.model.CorreoEnviado;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface CorreoEnviadoRepository extends MongoRepository<CorreoEnviado, String> {
}
