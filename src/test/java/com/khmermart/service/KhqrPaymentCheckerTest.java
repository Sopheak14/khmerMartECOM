package com.khmermart.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import com.khmermart.service.impl.KhqrPaymentCheckerImpl;
import com.khmermart.service.impl.KhqrPollingJob;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Bakong client is mocked; covers the shared checker and the runtime-mode poller. */
class KhqrPaymentCheckerTest {

  private static final String MD5 = "f90a388e82ae3ce9d76709697a293a16";

  private PaymentRepository paymentRepository;
  private OrderRepository orderRepository;
  private BakongApiClient bakongApiClient;
  private PaymentConfirmationService confirmation;
  private SettingService settingService;
  private KhqrPaymentCheckerImpl checker;
  private KhqrPollingJob poller;
  private Payment payment;

  @BeforeEach
  void setUp() {
    AppProperty appProperty =
        new AppProperty(
            "http://localhost:3000",
            "http://localhost:4000/api",
            "uploads",
            new AppProperty.Jwt(
                "secret-access-token-key-must-be-very-long-and-secure-for-testing",
                "secret-refresh-token-key-must-be-very-long-and-secure-for-testing",
                Duration.ofMinutes(15),
                Duration.ofDays(7)),
            new AppProperty.Mail("no-reply@khmermart.com"),
            null,
            new AppProperty.Khqr(
                "MOCK", // startup mode is MOCK, like the real incident
                "INDIVIDUAL",
                "https://api-bakong.nbc.gov.kh",
                "token",
                "2026-12-31",
                "khmermart@bkrt",
                null,
                null,
                "KhmerMart",
                "Phnom Penh",
                "85512345678",
                "KhmerMart Online",
                "WEB-01",
                null,
                "USD",
                10,
                5,
                4100L));
    paymentRepository = mock(PaymentRepository.class);
    orderRepository = mock(OrderRepository.class);
    bakongApiClient = mock(BakongApiClient.class);
    confirmation = mock(PaymentConfirmationService.class);
    settingService = mock(SettingService.class);

    checker =
        new KhqrPaymentCheckerImpl(
            appProperty, paymentRepository, orderRepository, bakongApiClient, confirmation);
    poller = new KhqrPollingJob(appProperty, settingService, paymentRepository, checker);

    payment =
        Payment.builder()
            .id(2089L)
            .orderId(42L)
            .method(PaymentMethod.KHQR)
            .status(PaymentStatus.PENDING)
            .amountCents(159L)
            .currency("USD")
            .khqrMd5(MD5)
            .qrExpiresAt(Instant.now().plusSeconds(300))
            .build();
    when(paymentRepository.findById(2089L)).thenReturn(Optional.of(payment));
    when(paymentRepository.save(any(Payment.class))).thenAnswer(i -> i.getArgument(0));
    when(orderRepository.findById(42L))
        .thenReturn(
            Optional.of(
                Order.builder()
                    .id(42L)
                    .orderNumber("KM-20261101-000042")
                    .status(OrderStatus.PENDING_PAYMENT)
                    .build()));
    when(confirmation.confirm(eq(2089L), any(BakongTxData.class))).thenReturn(payment);
    when(confirmation.confirmLate(eq(2089L), any(BakongTxData.class))).thenReturn(payment);
  }

  private static BakongCheckResponse found(String to, String currency, String amount) {
    return new BakongCheckResponse(
        0,
        "Success",
        null,
        new BakongTxData(
            "HASH123",
            "customer@abaa",
            to,
            currency,
            new BigDecimal(amount),
            null,
            1L,
            2L,
            "REF1"));
  }

  @Test
  @DisplayName("found: confirms normally and counts the check")
  void found() {
    when(bakongApiClient.checkTransactionByMd5(MD5))
        .thenReturn(found("khmermart@bkrt", "USD", "1.59"));

    PaymentCheckResultDto res = checker.check(2089L, "TEST");

    assertThat(res.outcome()).isEqualTo(Outcome.PAID);
    assertThat(res.bakongResponseCode()).isEqualTo(0);
    assertThat(payment.getCheckCount()).isEqualTo(1);
    assertThat(payment.getLastCheckedAt()).isNotNull();
    verify(confirmation).confirm(eq(2089L), any(BakongTxData.class));
    verify(confirmation, never()).confirmLate(any(), any());
  }

  @Test
  @DisplayName("not found: stays PENDING, still increments check_count")
  void notFound() {
    when(bakongApiClient.checkTransactionByMd5(MD5))
        .thenReturn(new BakongCheckResponse(1, "Transaction not found", 3, null));

    PaymentCheckResultDto res = checker.check(2089L, "TEST");

    assertThat(res.outcome()).isEqualTo(Outcome.NOT_FOUND);
    assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
    assertThat(payment.getCheckCount()).isEqualTo(1);
    verify(confirmation, never()).confirm(any(), any(BakongTxData.class));
  }

  @Test
  @DisplayName("Bakong unreachable (null response): BAKONG_ERROR, still counted")
  void bakongError() {
    when(bakongApiClient.checkTransactionByMd5(MD5)).thenReturn(null);
    assertThat(checker.check(2089L, "TEST").outcome()).isEqualTo(Outcome.BAKONG_ERROR);
    assertThat(payment.getCheckCount()).isEqualTo(1);
    assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
  }

  @Test
  @DisplayName("amount mismatch: rejected and FAILED with reason")
  void amountMismatch() {
    when(bakongApiClient.checkTransactionByMd5(MD5))
        .thenReturn(found("khmermart@bkrt", "USD", "0.10"));

    PaymentCheckResultDto res = checker.check(2089L, "TEST");

    assertThat(res.outcome()).isEqualTo(Outcome.REJECTED);
    assertThat(res.reason()).contains("Amount mismatch").contains("1.59").contains("0.10");
    assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
    verify(confirmation, never()).confirm(any(), any(BakongTxData.class));
  }

  @Test
  @DisplayName("account mismatch: rejected and FAILED with reason")
  void accountMismatch() {
    when(bakongApiClient.checkTransactionByMd5(MD5))
        .thenReturn(found("someone@else", "USD", "1.59"));

    PaymentCheckResultDto res = checker.check(2089L, "TEST");

    assertThat(res.outcome()).isEqualTo(Outcome.REJECTED);
    assertThat(res.reason()).contains("Recipient account mismatch");
    assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
  }

  @Test
  @DisplayName("currency mismatch: rejected")
  void currencyMismatch() {
    when(bakongApiClient.checkTransactionByMd5(MD5))
        .thenReturn(found("khmermart@bkrt", "KHR", "1.59"));
    assertThat(checker.check(2089L, "TEST").outcome()).isEqualTo(Outcome.REJECTED);
  }

  @Test
  @DisplayName("late payment: EXPIRED payment confirmed by Bakong is recorded via confirmLate")
  void latePayment() {
    payment.setStatus(PaymentStatus.EXPIRED);
    payment.setQrExpiresAt(Instant.now().minusSeconds(120));
    when(bakongApiClient.checkTransactionByMd5(MD5))
        .thenReturn(found("khmermart@bkrt", "USD", "1.59"));

    PaymentCheckResultDto res = checker.check(2089L, "ADMIN_RECHECK");

    assertThat(res.outcome()).isEqualTo(Outcome.LATE_PAID);
    verify(confirmation).confirmLate(eq(2089L), any(BakongTxData.class));
    verify(confirmation, never()).confirm(any(), any(BakongTxData.class));
  }

  @Test
  @DisplayName(
      "late payment: order already PAYMENT_EXPIRED while payment still PENDING is late too")
  void latePaymentOrderExpired() {
    when(orderRepository.findById(42L))
        .thenReturn(
            Optional.of(Order.builder().id(42L).status(OrderStatus.PAYMENT_EXPIRED).build()));
    when(bakongApiClient.checkTransactionByMd5(MD5))
        .thenReturn(found("khmermart@bkrt", "USD", "1.59"));

    assertThat(checker.check(2089L, "TEST").outcome()).isEqualTo(Outcome.LATE_PAID);
  }

  @Test
  @DisplayName("late + mismatch: an expired payment is not flipped to FAILED")
  void expiredMismatchStaysExpired() {
    payment.setStatus(PaymentStatus.EXPIRED);
    when(bakongApiClient.checkTransactionByMd5(MD5))
        .thenReturn(found("khmermart@bkrt", "USD", "9.99"));
    assertThat(checker.check(2089L, "TEST").outcome()).isEqualTo(Outcome.REJECTED);
    assertThat(payment.getStatus()).isEqualTo(PaymentStatus.EXPIRED);
  }

  @Test
  @DisplayName("already PAID / non-checkable payments never call Bakong")
  void skipped() {
    payment.setStatus(PaymentStatus.PAID);
    assertThat(checker.check(2089L, "TEST").outcome()).isEqualTo(Outcome.ALREADY_PAID);
    payment.setStatus(PaymentStatus.FAILED);
    assertThat(checker.check(2089L, "TEST").outcome()).isEqualTo(Outcome.SKIPPED);
    verify(bakongApiClient, never()).checkTransactionByMd5(any());
  }

  @Test
  @DisplayName("mode switched at runtime: poller idle in MOCK, checks once Settings says LIVE")
  void modeSwitchedAtRuntime() {
    when(paymentRepository.findByMethodAndStatus(PaymentMethod.KHQR, PaymentStatus.PENDING))
        .thenReturn(List.of(payment));
    when(paymentRepository.findByMethodAndStatusAndQrExpiresAtAfter(any(), any(), any()))
        .thenReturn(List.of());
    when(bakongApiClient.checkTransactionByMd5(MD5))
        .thenReturn(found("khmermart@bkrt", "USD", "1.59"));

    // startup env says MOCK, Settings says MOCK -> idle
    when(settingService.getKhqrMode()).thenReturn("MOCK");
    poller.pollPendingPayments();
    verify(bakongApiClient, never()).checkTransactionByMd5(any());
    assertThat(payment.getCheckCount()).isZero();

    // admin flips to LIVE in Settings; env property is still MOCK
    when(settingService.getKhqrMode()).thenReturn("LIVE");
    poller.pollPendingPayments();
    verify(bakongApiClient, times(1)).checkTransactionByMd5(MD5);
    assertThat(payment.getCheckCount()).isEqualTo(1);
    verify(confirmation).confirm(eq(2089L), any(BakongTxData.class));
  }

  @Test
  @DisplayName("poller also watches recently EXPIRED payments for late payment")
  void pollerWatchesExpired() {
    payment.setStatus(PaymentStatus.EXPIRED);
    payment.setQrExpiresAt(Instant.now().minusSeconds(600));
    when(settingService.getKhqrMode()).thenReturn("LIVE");
    when(paymentRepository.findByMethodAndStatus(PaymentMethod.KHQR, PaymentStatus.PENDING))
        .thenReturn(List.of());
    when(paymentRepository.findByMethodAndStatusAndQrExpiresAtAfter(
            eq(PaymentMethod.KHQR), eq(PaymentStatus.EXPIRED), any()))
        .thenReturn(List.of(payment));
    when(bakongApiClient.checkTransactionByMd5(MD5))
        .thenReturn(found("khmermart@bkrt", "USD", "1.59"));

    poller.pollPendingPayments();

    verify(confirmation).confirmLate(eq(2089L), any(BakongTxData.class));
  }
}
