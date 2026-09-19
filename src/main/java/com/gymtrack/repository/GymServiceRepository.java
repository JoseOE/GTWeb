package com.gymtrack.repository;

import com.gymtrack.model.GymService;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface GymServiceRepository extends MongoRepository<GymService, String> {
}
