package com.khmermart.service.impl;

import com.khmermart.common.exception.CustomException;
import com.khmermart.common.exception.ErrorCode;
import com.khmermart.config.AppProperty;
import com.khmermart.model.entity.Order;
import com.khmermart.model.entity.Payment;
import com.khmermart.model.enumerate.OrderStatus;
import com.khmermart.model.enumerate.PaymentMethod;
import com.khmermart.model.enumerate.PaymentStatus;
import com.khmermart.model.response.BakongCheckResponse;
import com.khmermart.model.response.BakongTxData;
import com.khmermart.model.response.PaymentCheckResultDto;
import com.khmermart.model.response.PaymentCheckResultDto.Outcome;
import com.khmermart.repository.OrderRepository;
import com.khmermart.repository.PaymentRepository;
import com.khmermart.service.BakongApiClient;
import com.khmermart.service.KhqrPaymentChecker;
import com.khmermart.service.PaymentConfirmationService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class KhqrPaymentCheckerImpl implements KhqrPaymentChecker {

  private static final Logger log = LoggerFactory.getLogger(KhqrPaymentCheckerImpl.class);

  private final AppProperty appProperty;
  private final PaymentRepository paymentRepository;
  private final OrderRepository orderRepository;
  private final BakongApiClient bakongApiClient;
  private final PaymentConfirmationService paymentConfirmationService;

  public KhqrPaymentCheckerImpl(
      AppProperty appProperty,
      PaymentRepository paymentRepository,
      OrderRepository orderRepository,
      BakongApiClient bakongApiClient,
      PaymentConfirmationService paymentConfirmationService) {
    this.appProperty = appProperty;
    this.paymentRepository = paymentRepository;
    this.orderRepository = orderRepository;
    this.bakongApiClient = bakongApiClient;
    this.paymentConfirmationService = paymentConfirmationService;
  }

  @Override
  public PaymentCheckResultDto check(Long paymentId, String trigger) {
    Payment payment =
        paymentRepository
            .findById(paymentId)
            .orElseThrow(() -> new CustomException(ErrorCode.NOT_FOUND, "Payment not found"));

    if (payment.getStatus() == PaymentStatus.PAID) {
      return result(payment, Outcome.ALREADY_PAID, "Payment is already PAID", null);
    }
    boolean checkable =
        payment.getMethod() == PaymentMethod.KHQR
            && payment.getKhqrMd5() != null
            && (payment.getStatus() == PaymentStatus.PENDING
                || payment.getStatus() == PaymentStatus.EXPIRED);
    if (!checkable) {
      return result(
          payment,
          Outcome.SKIPPED,
          "Not checkable (method=" + payment.getMethod() + ", status=" + payment.getStatus() + ")",
          null);
    }

    // Count the attempt first so check_count/last_checked_at move even if Bakong fails.
    payment.setLastCheckedAt(Instant.now());
    payment.setCheckCount(payment.getCheckCount() + 1);
    payment = paymentRepository.save(payment);

    BakongCheckResponse resp;
    try {
      resp = bakongApiClient.checkTransactionByMd5(payment.getKhqrMd5());
    } catch (Exception e) {
      return finish(
          payment, trigger, Outcome.BAKONG_ERROR, "Bakong call failed: " + e.getMessage(), null);
    }
    if (resp == null) {
      return finish(
          payment,
          trigger,
          Outcome.BAKONG_ERROR,
          "No usable response from Bakong (network/auth error, see BakongApiClient log)",
          null);
    }
    Integer code = resp.responseCode();
    if (code == null || code != 0 || resp.data() == null) {
      return finish(
          payment,
          trigger,
          Outcome.NOT_FOUND,
          "Bakong has no transaction yet: " + resp.responseMessage(),
          code);
    }

    BakongTxData tx = resp.data();
    String mismatch = findMismatch(payment, tx);
    if (mismatch != null) {
      if (payment.getStatus() == PaymentStatus.PENDING) {
        payment.setStatus(PaymentStatus.FAILED);
        payment.setFailureReason(mismatch);
        payment = paymentRepository.save(payment);
      }
      return finish(payment, trigger, Outcome.REJECTED, mismatch, code);
    }

    Order order = orderRepository.findById(payment.getOrderId()).orElse(null);
    boolean late =
        payment.getStatus() == PaymentStatus.EXPIRED
            || (order != null && order.getStatus() != OrderStatus.PENDING_PAYMENT);
    try {
      Payment saved =
          late
              ? paymentConfirmationService.confirmLate(payment.getId(), tx)
              : paymentConfirmationService.confirm(payment.getId(), tx);
      return finish(
          saved != null ? saved : payment,
          trigger,
          late ? Outcome.LATE_PAID : Outcome.PAID,
          "Accepted: amount="
              + tx.amount()
              + " "
              + tx.currency()
              + " toAccountId="
              + tx.toAccountId()
              + " hash="
              + tx.hash()
              + (late ? " (after expiry, order flagged for admin attention)" : ""),
          code);
    } catch (Exception e) {
      log.error(
          "KHQR check paymentId={} md5={} accepted by Bakong but confirm failed: {}",
          payment.getId(),
          payment.getKhqrMd5(),
          e.getMessage(),
          e);
      return finish(
          payment, trigger, Outcome.BAKONG_ERROR, "Confirm failed: " + e.getMessage(), code);
    }
  }

  /** Returns a human reason if the Bakong transaction does not match this payment, else null. */
  private String findMismatch(Payment payment, BakongTxData tx) {
    String expectedAccount = appProperty.khqr().accountId();
    if (expectedAccount == null || !expectedAccount.equalsIgnoreCase(tx.toAccountId())) {
      return "Recipient account mismatch: expected "
          + expectedAccount
          + ", got "
          + tx.toAccountId();
    }
    if (payment.getCurrency() == null || !payment.getCurrency().equalsIgnoreCase(tx.currency())) {
      return "Currency mismatch: expected " + payment.getCurrency() + ", got " + tx.currency();
    }
    BigDecimal expected =
        "USD".equalsIgnoreCase(payment.getCurrency())
            ? BigDecimal.valueOf(payment.getAmountCents())
                .movePointLeft(2)
                .setScale(2, RoundingMode.HALF_UP)
            : BigDecimal.valueOf(payment.getAmountKhr() != null ? payment.getAmountKhr() : 0);
    if (tx.amount() == null || expected.compareTo(tx.amount()) != 0) {
      return "Amount mismatch: expected " + expected + ", got " + tx.amount();
    }
    return null;
  }

  private PaymentCheckResultDto finish(
      Payment payment, String trigger, Outcome outcome, String reason, Integer code) {
    String line = "KHQR check paymentId={} md5={} trigger={} responseCode={} outcome={} reason={}";
    if (outcome == Outcome.BAKONG_ERROR || outcome == Outcome.REJECTED) {
      log.warn(line, payment.getId(), payment.getKhqrMd5(), trigger, code, outcome, reason);
    } else {
      log.info(line, payment.getId(), payment.getKhqrMd5(), trigger, code, outcome, reason);
    }
    return result(payment, outcome, reason, code);
  }

  private PaymentCheckResultDto result(
      Payment payment, Outcome outcome, String reason, Integer code) {
    return new PaymentCheckResultDto(
        payment.getId(), outcome, reason, code, payment.getStatus(), payment.getCheckCount());
  }
}
