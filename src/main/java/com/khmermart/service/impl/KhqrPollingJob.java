package com.khmermart.service.impl;

import com.khmermart.config.AppProperty;
import com.khmermart.model.entity.Payment;
import com.khmermart.model.enumerate.PaymentMethod;
import com.khmermart.model.enumerate.PaymentStatus;
import com.khmermart.repository.PaymentRepository;
import com.khmermart.service.KhqrPaymentChecker;
import com.khmermart.service.SettingService;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Polls Bakong for unpaid KHQR payments. The mode is read from Admin Settings on every tick (not
 * from the startup env) so switching MOCK -> LIVE at runtime takes effect immediately.
 */
@Component
public class KhqrPollingJob {

  private static final Logger log = LoggerFactory.getLogger(KhqrPollingJob.class);

  /** How long after expiry we keep looking for a customer who paid at the last second. */
  static final Duration LATE_WINDOW = Duration.ofHours(1);

  /** Expired payments are re-checked less often than live ones. */
  static final int EXPIRED_INTERVAL_SECONDS = 30;

  private static final int MAX_PER_TICK = 50;

  private final AppProperty appProperty;
  private final SettingService settingService;
  private final PaymentRepository paymentRepository;
  private final KhqrPaymentChecker paymentChecker;

  public KhqrPollingJob(
      AppProperty appProperty,
      SettingService settingService,
      PaymentRepository paymentRepository,
      KhqrPaymentChecker paymentChecker) {
    this.appProperty = appProperty;
    this.settingService = settingService;
    this.paymentRepository = paymentRepository;
    this.paymentChecker = paymentChecker;
  }

  @Scheduled(fixedDelayString = "${app.khqr.poll-interval-seconds:5}000")
  public void pollPendingPayments() {
    String mode = settingService.getKhqrMode();
    if (!"LIVE".equalsIgnoreCase(mode)) {
      log.trace("KHQR poller idle: mode={}", mode);
      return;
    }

    Instant now = Instant.now();
    int intervalSec =
        appProperty.khqr().pollIntervalSeconds() > 0 ? appProperty.khqr().pollIntervalSeconds() : 5;

    List<Payment> candidates =
        new ArrayList<>(
            paymentRepository.findByMethodAndStatus(PaymentMethod.KHQR, PaymentStatus.PENDING));
    candidates.addAll(
        paymentRepository.findByMethodAndStatusAndQrExpiresAtAfter(
            PaymentMethod.KHQR, PaymentStatus.EXPIRED, now.minus(LATE_WINDOW)));

    int count = 0;
    for (Payment payment : candidates) {
      if (count >= MAX_PER_TICK) {
        break;
      }
      if (payment.getKhqrMd5() == null) {
        continue;
      }
      int minGap =
          payment.getStatus() == PaymentStatus.EXPIRED ? EXPIRED_INTERVAL_SECONDS : intervalSec;
      if (payment.getLastCheckedAt() != null
          && Duration.between(payment.getLastCheckedAt(), now).getSeconds() < minGap) {
        continue;
      }
      count++;
      try {
        paymentChecker.check(payment.getId(), "POLLER");
      } catch (Exception e) {
        log.warn("Error polling payment {}: {}", payment.getId(), e.getMessage());
      }
    }
  }
}
