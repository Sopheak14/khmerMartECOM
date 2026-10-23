package com.khmermart.modules.payment.repository;

import com.khmermart.modules.payment.entity.Refund;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RefundRepository extends JpaRepository<Refund, Long> {
  List<Refund> findByPaymentId(Long paymentId);
}
