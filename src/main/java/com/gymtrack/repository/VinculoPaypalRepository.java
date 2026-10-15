package com.gymtrack.repository;

import com.gymtrack.model.VinculoPaypal;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface VinculoPaypalRepository extends MongoRepository<VinculoPaypal, String> {
}
