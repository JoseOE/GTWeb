package com.gymtrack.repository;

import com.gymtrack.model.Pedido;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface PedidoRepository extends MongoRepository<Pedido, String> {
    Optional<Pedido> findByOrderId(String orderId);
    List<Pedido> findByUserIdOrderByCreadoEnDesc(String userId);
    List<Pedido> findByGymIdOrderByCreadoEnDesc(String gymId);
}
