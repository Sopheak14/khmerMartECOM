package com.khmermart.service;

import com.khmermart.model.enumerate.PaymentMethod;
import com.khmermart.model.enumerate.PaymentStatus;
import com.khmermart.model.request.CompleteRefundRequest;
import com.khmermart.model.request.RefundRequest;
import com.khmermart.model.response.AdminPaymentDto;
import com.khmermart.model.response.PaymentCheckResultDto;
import com.khmermart.model.response.ReconciliationReportDto;
import java.time.Instant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface AdminPaymentService {

  Page<AdminPaymentDto> getPayments(
      PaymentStatus status, PaymentMethod method, Instant from, Instant to, Pageable pageable);

  AdminPaymentDto getPaymentDetail(Long id);

  AdminPaymentDto refundPayment(Long id, RefundRequest request, Long adminId, String ip);

  /**
   * ADMIN records that the manual refund transfer was made. Only from REFUND_PENDING; the bank
   * reference is mandatory.
   */
  AdminPaymentDto completeRefund(Long id, CompleteRefundRequest request, Long adminId, String ip);

  /** Runs one Bakong check for this payment right now (same logic as the poller). */
  PaymentCheckResultDto recheckPayment(Long id, Long adminId, String ip);

  byte[] exportPaymentsCsv();

  ReconciliationReportDto getReconciliationReport();
}
