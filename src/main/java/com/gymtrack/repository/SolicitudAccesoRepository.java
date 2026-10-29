package com.gymtrack.repository;

import com.gymtrack.model.SolicitudAcceso;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface SolicitudAccesoRepository extends MongoRepository<SolicitudAcceso, String> {
    List<SolicitudAcceso> findTop20ByGymIdOrderByCreadaEnDesc(String gymId);
    Optional<SolicitudAcceso> findFirstByLectorIdAndEpcAndEstadoAndCreadaEnAfter(String lectorId, String epc, String estado, Instant desde);
    Optional<SolicitudAcceso> findFirstByLectorIdAndUserIdAndEstadoAndCreadaEnAfter(String lectorId, String userId, String estado, Instant desde);
    Optional<SolicitudAcceso> findFirstByLectorIdAndCreadaEnAfterOrderByCreadaEnDesc(String lectorId, Instant desde);
    Optional<SolicitudAcceso> findFirstByLectorIdAndEstadoInAndComandoEntregadoFalseAndAtendidaEnAfter(
            String lectorId, Collection<String> estados, Instant desde);
}
