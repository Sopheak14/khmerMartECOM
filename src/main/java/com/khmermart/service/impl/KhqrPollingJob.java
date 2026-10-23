package com.khmermart.service.impl;

import com.khmermart.config.AppProperty;
import com.khmermart.model.entity.Payment;
import com.khmermart.model.enumerate.PaymentMethod;
import com.khmermart.model.enumerate.PaymentStatus;
import com.khmermart.model.response.BakongCheckResponse;
import com.khmermart.repository.PaymentRepository;
import com.khmermart.service.BakongApiClient;
import com.khmermart.service.PaymentConfirmationService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class KhqrPollingJob {

  private static final Logger log = LoggerFactory.getLogger(KhqrPollingJob.class);

  private final AppProperty appProperty;
  private final PaymentRepository paymentRepository;
  private final BakongApiClient bakongApiClient;
  private final PaymentConfirmationService paymentConfirmationService;

  public KhqrPollingJob(
      AppProperty appProperty,
      PaymentRepository paymentRepository,
      BakongApiClient bakongApiClient,
      PaymentConfirmationService paymentConfirmationService) {
    this.appProperty = appProperty;
    this.paymentRepository = paymentRepository;
    this.bakongApiClient = bakongApiClient;
    this.paymentConfirmationService = paymentConfirmationService;
  }

  @Scheduled(fixedDelayString = "${app.khqr.poll-interval-seconds:5}000")
  public void pollPendingPayments() {
    if (!"LIVE".equalsIgnoreCase(appProperty.khqr().mode())) {
      return;
    }

    Instant now = Instant.now();
    int intervalSec =
        appProperty.khqr().pollIntervalSeconds() > 0 ? appProperty.khqr().pollIntervalSeconds() : 5;

    List<Payment> pendingPayments =
        paymentRepository.findByMethodAndStatus(PaymentMethod.KHQR, PaymentStatus.PENDING);

    int count = 0;
    for (Payment payment : pendingPayments) {
      if (count >= 50) {
        break;
      }
      if (payment.getKhqrMd5() == null) {
        continue;
      }
      if (payment.getQrExpiresAt() != null
          && payment.getQrExpiresAt().plus(Duration.ofMinutes(1)).isBefore(now)) {
        continue;
      }
      if (payment.getLastCheckedAt() != null
          && Duration.between(payment.getLastCheckedAt(), now).getSeconds() < intervalSec) {
        continue;
      }

      count++;
      payment.setLastCheckedAt(now);
      payment.setCheckCount(payment.getCheckCount() + 1);
      paymentRepository.save(payment);

      try {
        BakongCheckResponse resp = bakongApiClient.checkTransactionByMd5(payment.getKhqrMd5());
        if (resp != null
            && resp.responseCode() != null
            && resp.responseCode() == 0
            && resp.data() != null) {

          // Validate recipient
          if (!appProperty.khqr().accountId().equalsIgnoreCase(resp.data().toAccountId())) {
            log.error(
                "Payment {} account mismatch: expected {}, got {}",
                payment.getId(),
                appProperty.khqr().accountId(),
                resp.data().toAccountId());
            payment.setStatus(PaymentStatus.FAILED);
            payment.setFailureReason("Recipient account mismatch");
            paymentRepository.save(payment);
            continue;
          }

          // Validate currency
          if (!payment.getCurrency().equalsIgnoreCase(resp.data().currency())) {
            log.error(
                "Payment {} currency mismatch: expected {}, got {}",
                payment.getId(),
                payment.getCurrency(),
                resp.data().currency());
            payment.setStatus(PaymentStatus.FAILED);
            payment.setFailureReason("Currency mismatch");
            paymentRepository.save(payment);
            continue;
          }

          // Validate amount
          BigDecimal expectedAmount =
              "USD".equalsIgnoreCase(payment.getCurrency())
                  ? BigDecimal.valueOf(payment.getAmountCents())
                      .movePointLeft(2)
                      .setScale(2, RoundingMode.HALF_UP)
                  : BigDecimal.valueOf(payment.getAmountKhr() != null ? payment.getAmountKhr() : 0);

          if (expectedAmount.compareTo(resp.data().amount()) != 0) {
            log.error(
                "Payment {} amount mismatch: expected {}, got {}",
                payment.getId(),
                expectedAmount,
                resp.data().amount());
            payment.setStatus(PaymentStatus.FAILED);
            payment.setFailureReason("Amount mismatch");
            paymentRepository.save(payment);
            continue;
          }

          paymentConfirmationService.confirm(payment.getId(), resp.data());
        }
      } catch (Exception e) {
        log.warn("Error polling payment {}: {}", payment.getId(), e.getMessage());
      }
    }
  }
}
