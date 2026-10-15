package com.gymtrack.repository;

import com.gymtrack.model.Resena;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface ResenaRepository extends MongoRepository<Resena, String> {
    Optional<Resena> findByUserIdAndProductoId(String userId, String productoId);
    List<Resena> findByProductoIdAndOcultaFalseOrderByActualizadoEnDesc(String productoId, Pageable pagina);
    List<Resena> findByGymIdOrderByActualizadoEnDesc(String gymId, Pageable pagina);
    Optional<Resena> findByIdAndGymId(String id, String gymId);
}
