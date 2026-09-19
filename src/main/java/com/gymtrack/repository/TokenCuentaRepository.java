package com.gymtrack.repository;

import com.gymtrack.model.TokenCuenta;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface TokenCuentaRepository extends MongoRepository<TokenCuenta, String> {
    Optional<TokenCuenta> findFirstByUserIdAndTipoOrderByCreadoDesc(String userId, String tipo);
    Optional<TokenCuenta> findByEnlaceHashAndTipo(String enlaceHash, String tipo);
    void deleteByUserIdAndTipo(String userId, String tipo);
}
