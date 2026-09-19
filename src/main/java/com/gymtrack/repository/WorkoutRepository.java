package com.gymtrack.repository;

import com.gymtrack.model.WorkoutSession;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface WorkoutRepository extends MongoRepository<WorkoutSession, String> {
    List<WorkoutSession> findByUserIdOrderByFechaDesc(String userId);
}
