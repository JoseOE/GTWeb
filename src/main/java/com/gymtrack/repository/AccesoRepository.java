package com.gymtrack.repository;

import com.gymtrack.model.Acceso;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface AccesoRepository extends MongoRepository<Acceso, String> {
    List<Acceso> findTop40ByGymIdOrderByFechaDesc(String gymId);
    Optional<Acceso> findFirstByUserIdAndTipoInOrderByFechaDesc(String userId, Collection<String> tipos);
    Optional<Acceso> findFirstByLectorIdAndPorRostroTrueAndFechaAfterOrderByFechaDesc(String lectorId, java.time.Instant desde);
}
