package com.khmermart.modules.payment.repository;

import com.khmermart.modules.payment.entity.Payment;
import com.khmermart.modules.payment.model.PaymentMethod;
import com.khmermart.modules.payment.model.PaymentStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface PaymentRepository
    extends JpaRepository<Payment, Long>, JpaSpecificationExecutor<Payment> {
  Optional<Payment> findByOrderId(Long orderId);

  Optional<Payment> findByOrderIdAndStatus(Long orderId, PaymentStatus status);

  Optional<Payment> findByKhqrMd5(String khqrMd5);

  Optional<Payment> findByBakongHash(String bakongHash);

  @Query(
      """
            SELECT p FROM Payment p
            WHERE p.method = :method
              AND p.status = :status
              AND p.qrExpiresAt > :minExpiry
              AND (p.lastCheckedAt IS NULL OR p.lastCheckedAt < :checkThreshold)
            ORDER BY p.id ASC
            """)
  List<Payment> findKhqrPaymentsToPoll(
      @Param("method") PaymentMethod method,
      @Param("status") PaymentStatus status,
      @Param("minExpiry") Instant minExpiry,
      @Param("checkThreshold") Instant checkThreshold,
      Pageable pageable);

  @Query(
      """
            SELECT p FROM Payment p
            WHERE p.method = :method
              AND p.status = :status
              AND p.qrExpiresAt < :cutoff
            """)
  List<Payment> findExpiredPaymentsToProcess(
      @Param("method") PaymentMethod method,
      @Param("status") PaymentStatus status,
      @Param("cutoff") Instant cutoff);
}
