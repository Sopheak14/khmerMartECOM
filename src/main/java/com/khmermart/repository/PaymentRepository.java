package com.khmermart.repository;

import com.khmermart.model.entity.Payment;
import com.khmermart.model.enumerate.PaymentStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {
  Optional<Payment> findByOrderId(Long orderId);

  Optional<Payment> findByKhqrMd5(String khqrMd5);

  Optional<Payment> findByBakongHash(String bakongHash);

  List<Payment> findByStatusAndQrExpiresAtBefore(PaymentStatus status, Instant before);
}
