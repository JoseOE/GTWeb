package com.gymtrack.repository;

import com.gymtrack.model.Routine;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface RoutineRepository extends MongoRepository<Routine, String> {
    List<Routine> findByGymId(String gymId);
}
