package com.gymtrack.repository;

import com.gymtrack.model.Payment;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends MongoRepository<Payment, String> {
    List<Payment> findByUserIdOrderByFechaPagoDesc(String userId);
    List<Payment> findByGymIdOrderByFechaPagoDesc(String gymId);
    // Los pagos más recientes del gimnasio (el orden y el tope los pone el Pageable).
    List<Payment> findByGymId(String gymId, Pageable pagina);
    List<Payment> findByGymIdAndFechaPagoGreaterThanEqual(String gymId, LocalDate desde, Pageable pagina);
    Optional<Payment> findByOrderId(String orderId);
}
