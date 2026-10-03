package com.gymtrack.repository;

import com.gymtrack.model.OrdenPaypal;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface OrdenPaypalRepository extends MongoRepository<OrdenPaypal, String> {
}
