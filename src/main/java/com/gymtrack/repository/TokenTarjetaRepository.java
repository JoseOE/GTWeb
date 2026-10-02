package com.gymtrack.repository;

import com.gymtrack.model.TokenTarjeta;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface TokenTarjetaRepository extends MongoRepository<TokenTarjeta, String> {
}
