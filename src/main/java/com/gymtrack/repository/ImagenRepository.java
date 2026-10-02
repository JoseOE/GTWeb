package com.gymtrack.repository;

import com.gymtrack.model.Imagen;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface ImagenRepository extends MongoRepository<Imagen, String> {
}
