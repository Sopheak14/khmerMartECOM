package com.khmermart.service;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.khmermart.config.AppProperty;
import com.khmermart.model.entity.Payment;
import com.khmermart.model.enumerate.PaymentMethod;
import com.khmermart.model.enumerate.PaymentStatus;
import com.khmermart.model.response.BakongCheckResponse;
import com.khmermart.repository.PaymentRepository;
import com.khmermart.service.impl.BakongApiClientImpl;
import com.khmermart.service.impl.KhqrPollingJob;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

class BakongApiClientWireMockTest {

  private WireMockServer wireMockServer;
  private BakongApiClientImpl bakongApiClient;
  private AppProperty appProperty;
  private PaymentRepository paymentRepository;
  private PaymentConfirmationService paymentConfirmationService;
  private KhqrPollingJob khqrPollingJob;

  @BeforeEach
  void setUp() {
    wireMockServer = new WireMockServer(wireMockConfig().dynamicPort());
    wireMockServer.start();

    appProperty =
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
                "LIVE",
                "INDIVIDUAL",
                wireMockServer.baseUrl(),
                "test-bakong-token",
                "2026-12-31",
                "khmermart@aclb",
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

    bakongApiClient = new BakongApiClientImpl(appProperty, RestClient.builder());
    paymentRepository = mock(PaymentRepository.class);
    paymentConfirmationService = mock(PaymentConfirmationService.class);

    khqrPollingJob =
        new KhqrPollingJob(
            appProperty, paymentRepository, bakongApiClient, paymentConfirmationService);
  }

  @AfterEach
  void tearDown() {
    if (wireMockServer != null) {
      wireMockServer.stop();
    }
  }

  @Test
  @DisplayName("WireMock: paid (code 0) transitions payment to PAID")
  void testPaid() {
    wireMockServer.stubFor(
        post(urlEqualTo("/v1/check_transaction_by_md5"))
            .withHeader("Authorization", equalTo("Bearer test-bakong-token"))
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        """
                        {
                          "responseCode": 0,
                          "responseMessage": "Success",
                          "errorCode": null,
                          "data": {
                            "hash": "BAKONG-HASH-PAID-001",
                            "fromAccountId": "customer@aclb",
                            "toAccountId": "khmermart@aclb",
                            "currency": "USD",
                            "amount": 894.00,
                            "description": "Paid order",
                            "createdDateMs": 1729670000000,
                            "acknowledgedDateMs": 1729670001000,
                            "externalRef": "KM-20261023-0001"
                          }
                        }
                        """)));

    Payment payment =
        Payment.builder()
            .id(1L)
            .orderId(10L)
            .method(PaymentMethod.KHQR)
            .status(PaymentStatus.PENDING)
            .amountCents(89400L)
            .currency("USD")
            .khqrMd5("test-md5-paid")
            .qrExpiresAt(Instant.now().plusSeconds(600))
            .build();

    when(paymentRepository.findByMethodAndStatus(PaymentMethod.KHQR, PaymentStatus.PENDING))
        .thenReturn(List.of(payment));

    khqrPollingJob.pollPendingPayments();

    verify(paymentConfirmationService).confirm(eq(1L), any());
    assertThat(payment.getStatus())
        .isEqualTo(PaymentStatus.PENDING); // updated by confirmation service
  }

  @Test
  @DisplayName("WireMock: not found (code 1) leaves payment PENDING")
  void testNotFound() {
    wireMockServer.stubFor(
        post(urlEqualTo("/v1/check_transaction_by_md5"))
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        """
                        {
                          "responseCode": 1,
                          "responseMessage": "Transaction not found",
                          "errorCode": null,
                          "data": null
                        }
                        """)));

    Payment payment =
        Payment.builder()
            .id(2L)
            .orderId(20L)
            .method(PaymentMethod.KHQR)
            .status(PaymentStatus.PENDING)
            .amountCents(5000L)
            .currency("USD")
            .khqrMd5("test-md5-not-found")
            .qrExpiresAt(Instant.now().plusSeconds(600))
            .build();

    when(paymentRepository.findByMethodAndStatus(PaymentMethod.KHQR, PaymentStatus.PENDING))
        .thenReturn(List.of(payment));

    khqrPollingJob.pollPendingPayments();

    verify(paymentConfirmationService, never()).confirm(any(), any());
    assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
  }

  @Test
  @DisplayName("WireMock: amount mismatch marks payment FAILED")
  void testAmountMismatch() {
    wireMockServer.stubFor(
        post(urlEqualTo("/v1/check_transaction_by_md5"))
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        """
                        {
                          "responseCode": 0,
                          "responseMessage": "Success",
                          "errorCode": null,
                          "data": {
                            "hash": "BAKONG-HASH-MISMATCH",
                            "fromAccountId": "customer@aclb",
                            "toAccountId": "khmermart@aclb",
                            "currency": "USD",
                            "amount": 10.00,
                            "description": "Tampered amount"
                          }
                        }
                        """)));

    Payment payment =
        Payment.builder()
            .id(3L)
            .orderId(30L)
            .method(PaymentMethod.KHQR)
            .status(PaymentStatus.PENDING)
            .amountCents(89400L)
            .currency("USD")
            .khqrMd5("test-md5-tampered")
            .qrExpiresAt(Instant.now().plusSeconds(600))
            .build();

    when(paymentRepository.findByMethodAndStatus(PaymentMethod.KHQR, PaymentStatus.PENDING))
        .thenReturn(List.of(payment));

    khqrPollingJob.pollPendingPayments();

    verify(paymentConfirmationService, never()).confirm(any(), any());
    assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
    assertThat(payment.getFailureReason()).isEqualTo("Amount mismatch");
  }

  @Test
  @DisplayName("WireMock: wrong receiver account marks payment FAILED")
  void testWrongReceiver() {
    wireMockServer.stubFor(
        post(urlEqualTo("/v1/check_transaction_by_md5"))
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        """
                        {
                          "responseCode": 0,
                          "responseMessage": "Success",
                          "errorCode": null,
                          "data": {
                            "hash": "BAKONG-HASH-WRONG-RECEIVER",
                            "fromAccountId": "customer@aclb",
                            "toAccountId": "other_account@aclb",
                            "currency": "USD",
                            "amount": 894.00,
                            "description": "Wrong destination"
                          }
                        }
                        """)));

    Payment payment =
        Payment.builder()
            .id(4L)
            .orderId(40L)
            .method(PaymentMethod.KHQR)
            .status(PaymentStatus.PENDING)
            .amountCents(89400L)
            .currency("USD")
            .khqrMd5("test-md5-wrong-receiver")
            .qrExpiresAt(Instant.now().plusSeconds(600))
            .build();

    when(paymentRepository.findByMethodAndStatus(PaymentMethod.KHQR, PaymentStatus.PENDING))
        .thenReturn(List.of(payment));

    khqrPollingJob.pollPendingPayments();

    verify(paymentConfirmationService, never()).confirm(any(), any());
    assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
    assertThat(payment.getFailureReason()).isEqualTo("Recipient account mismatch");
  }

  @Test
  @DisplayName("WireMock: HTTP 401 token expired keeps payment PENDING and alerts admin")
  void testTokenExpired401() {
    wireMockServer.stubFor(
        post(urlEqualTo("/v1/check_transaction_by_md5")).willReturn(aResponse().withStatus(401)));

    Payment payment =
        Payment.builder()
            .id(5L)
            .orderId(50L)
            .method(PaymentMethod.KHQR)
            .status(PaymentStatus.PENDING)
            .amountCents(1000L)
            .currency("USD")
            .khqrMd5("test-md5-401")
            .qrExpiresAt(Instant.now().plusSeconds(600))
            .build();

    when(paymentRepository.findByMethodAndStatus(PaymentMethod.KHQR, PaymentStatus.PENDING))
        .thenReturn(List.of(payment));

    khqrPollingJob.pollPendingPayments();

    verify(paymentConfirmationService, never()).confirm(any(), any());
    assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
  }

  @Test
  @DisplayName("WireMock: timeout / network error keeps payment PENDING")
  void testTimeout() {
    wireMockServer.stubFor(
        post(urlEqualTo("/v1/check_transaction_by_md5"))
            .willReturn(
                aResponse()
                    .withFixedDelay(9000) // greater than readTimeout 8000ms
                    .withStatus(200)));

    BakongCheckResponse resp = bakongApiClient.checkTransactionByMd5("timeout-md5");
    assertThat(resp).isNull();
  }
}
