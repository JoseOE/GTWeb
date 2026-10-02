package com.gymtrack.repository;

import com.gymtrack.model.Payment;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends MongoRepository<Payment, String> {
    List<Payment> findByUserIdOrderByFechaPagoDesc(String userId);
    List<Payment> findByGymIdOrderByFechaPagoDesc(String gymId);
    Optional<Payment> findByOrderId(String orderId);
}
