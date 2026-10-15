package com.gymtrack.repository;

import com.gymtrack.model.EnlaceRecibo;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface EnlaceReciboRepository extends MongoRepository<EnlaceRecibo, String> {
}
