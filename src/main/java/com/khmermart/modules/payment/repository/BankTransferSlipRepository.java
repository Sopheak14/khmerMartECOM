package com.khmermart.modules.payment.repository;

import com.khmermart.modules.payment.entity.BankTransferSlip;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BankTransferSlipRepository extends JpaRepository<BankTransferSlip, Long> {
  Optional<BankTransferSlip> findByPaymentId(Long paymentId);
}
