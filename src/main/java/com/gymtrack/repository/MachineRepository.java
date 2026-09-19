package com.gymtrack.repository;

import com.gymtrack.model.Machine;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface MachineRepository extends MongoRepository<Machine, String> {
    List<Machine> findByGymId(String gymId);
    long countByGymId(String gymId);
}
