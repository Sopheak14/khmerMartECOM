package com.khmermart.repository;

import com.khmermart.model.entity.Payment;
import com.khmermart.model.enumerate.PaymentMethod;
import com.khmermart.model.enumerate.PaymentStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface PaymentRepository
    extends JpaRepository<Payment, Long>, JpaSpecificationExecutor<Payment> {
  Optional<Payment> findByOrderId(Long orderId);

  List<Payment> findByOrderIdOrderByIdDesc(Long orderId);

  Optional<Payment> findFirstByOrderIdOrderByIdDesc(Long orderId);

  Optional<Payment> findByOrderIdAndStatus(Long orderId, PaymentStatus status);

  Optional<Payment> findByKhqrMd5(String khqrMd5);

  Optional<Payment> findByBakongHash(String bakongHash);

  List<Payment> findByStatusAndQrExpiresAtBefore(PaymentStatus status, Instant before);

  List<Payment> findByMethodAndStatus(PaymentMethod method, PaymentStatus status);

  List<Payment> findByMethodAndStatusAndQrExpiresAtAfter(
      PaymentMethod method, PaymentStatus status, Instant after);
}
