package com.gymtrack.repository;

import com.gymtrack.model.SesionStripe;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface SesionStripeRepository extends MongoRepository<SesionStripe, String> {
}
