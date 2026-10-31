package com.khmermart.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.khmermart.config.AppProperty;
import com.khmermart.model.entity.Order;
import com.khmermart.model.entity.Payment;
import com.khmermart.model.enumerate.OrderStatus;
import com.khmermart.model.enumerate.PaymentMethod;
import com.khmermart.model.enumerate.PaymentStatus;
import com.khmermart.model.response.KhqrPaymentDto;
import com.khmermart.repository.OrderRepository;
import com.khmermart.repository.PaymentRepository;
import com.khmermart.security.JwtService;
import com.khmermart.service.impl.KhqrServiceImpl;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import kh.gov.nbc.bakong_khqr.BakongKHQR;
import kh.gov.nbc.bakong_khqr.model.CRCValidation;
import kh.gov.nbc.bakong_khqr.model.KHQRDecodeData;
import kh.gov.nbc.bakong_khqr.model.KHQRResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class KhqrServiceTest {

  private AppProperty appProperty;
  private OrderRepository orderRepository;
  private PaymentRepository paymentRepository;
  private PaymentConfirmationService paymentConfirmationService;
  private BakongApiClient bakongApiClient;
  private JwtService jwtService;
  private KhqrServiceImpl khqrService;

  @BeforeEach
  void setUp() {
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
                "MOCK",
                "INDIVIDUAL",
                "https://api-bakong.nbc.gov.kh",
                "token",
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

    orderRepository = mock(OrderRepository.class);
    paymentRepository = mock(PaymentRepository.class);
    paymentConfirmationService = mock(PaymentConfirmationService.class);
    bakongApiClient = mock(BakongApiClient.class);
    jwtService = mock(JwtService.class);

    khqrService =
        new KhqrServiceImpl(
            appProperty,
            orderRepository,
            paymentRepository,
            paymentConfirmationService,
            jwtService,
            mock(
                SettingService.class,
                inv -> inv.getMethod().getName().equals("getKhqrMode") ? "MOCK" : null),
            mock(KhqrPaymentChecker.class));
  }

  @Test
  @DisplayName("Generate USD KHQR: valid EMV string, 840 currency, correct amount and bill number")
  void testGenerateUsdKhqr() {
    Order order =
        Order.builder()
            .id(100L)
            .orderNumber("KM-20261023-000100")
            .userId(1L)
            .status(OrderStatus.PENDING_PAYMENT)
            .paymentStatus(PaymentStatus.PENDING)
            .paymentMethod(PaymentMethod.KHQR)
            .subtotalCents(89400L)
            .discountCents(0L)
            .shippingCents(0L)
            .totalCents(89400L)
            .currency("USD")
            .placedAt(Instant.now())
            .build();

    when(orderRepository.findByOrderNumber("KM-20261023-000100")).thenReturn(Optional.of(order));
    when(paymentRepository.findByOrderIdOrderByIdDesc(100L)).thenReturn(List.of());
    when(paymentRepository.save(any(Payment.class)))
        .thenAnswer(
            inv -> {
              Payment p = inv.getArgument(0);
              p.setId(501L);
              return p;
            });
    when(jwtService.generateStreamToken(any(), any())).thenReturn("mock-stream-token");

    KhqrPaymentDto result = khqrService.generateKhqr(1L, "KM-20261023-000100", "USD");

    assertThat(result).isNotNull();
    assertThat(result.orderNumber()).isEqualTo("KM-20261023-000100");
    assertThat(result.qrString()).startsWith("000201");
    assertThat(result.md5()).hasSize(32);
    assertThat(result.qrImageDataUrl()).startsWith("data:image/png;base64,");
    assertThat(result.expiresAt()).isAfter(Instant.now());
    assertThat(result.amount()).isEqualTo(894.00);

    // Verify CRC with NBC SDK
    KHQRResponse<CRCValidation> verifyRes = BakongKHQR.verify(result.qrString());
    assertThat(verifyRes).isNotNull();
    assertThat(verifyRes.getData()).isNotNull();
    assertThat(verifyRes.getData().isValid()).isTrue();

    // Decode with NBC SDK
    KHQRResponse<KHQRDecodeData> decodeRes = BakongKHQR.decode(result.qrString());
    assertThat(decodeRes).isNotNull();
    KHQRDecodeData data = decodeRes.getData();
    assertThat(data).isNotNull();
    assertThat(data.getTransactionAmount()).isEqualTo("894");
    assertThat(data.getTransactionCurrency()).isEqualTo("840"); // 840 = USD
    assertThat(data.getBillNumber()).isEqualTo("KM-20261023-000100");
    assertThat(data.getMerchantName()).isEqualTo("KhmerMart");
    assertThat(data.getMerchantCity()).isEqualTo("Phnom Penh");
    assertThat(data.getExpirationTimestamp()).isNotNull();
    assertThat(Long.parseLong(data.getExpirationTimestamp()))
        .isGreaterThan(Instant.now().toEpochMilli());
  }

  @Test
  @DisplayName("Generate KHR KHQR: integer riel, 116 currency code")
  void testGenerateKhrKhqr() {
    Order order =
        Order.builder()
            .id(101L)
            .orderNumber("KM-20261023-000101")
            .userId(1L)
            .status(OrderStatus.PENDING_PAYMENT)
            .paymentStatus(PaymentStatus.PENDING)
            .paymentMethod(PaymentMethod.KHQR)
            .subtotalCents(89400L)
            .discountCents(0L)
            .shippingCents(0L)
            .totalCents(89400L)
            .currency("USD")
            .placedAt(Instant.now())
            .build();

    when(orderRepository.findByOrderNumber("KM-20261023-000101")).thenReturn(Optional.of(order));
    when(paymentRepository.findByOrderIdOrderByIdDesc(101L)).thenReturn(List.of());
    when(paymentRepository.save(any(Payment.class)))
        .thenAnswer(
            inv -> {
              Payment p = inv.getArgument(0);
              p.setId(502L);
              return p;
            });
    when(jwtService.generateStreamToken(any(), any())).thenReturn("mock-stream-token");

    KhqrPaymentDto result = khqrService.generateKhqr(1L, "KM-20261023-000101", "KHR");

    assertThat(result).isNotNull();
    assertThat(result.currency()).isEqualTo("KHR");
    long expectedKhr = Math.round(894.00 * 4100); // 3,665,400 riel
    assertThat(result.amount()).isEqualTo((double) expectedKhr);

    // Decode with NBC SDK
    KHQRResponse<KHQRDecodeData> decodeRes = BakongKHQR.decode(result.qrString());
    assertThat(decodeRes).isNotNull();
    KHQRDecodeData data = decodeRes.getData();
    assertThat(data).isNotNull();
    assertThat(data.getTransactionAmount()).isEqualTo(String.valueOf(expectedKhr));
    assertThat(data.getTransactionCurrency()).isEqualTo("116"); // 116 = KHR
    assertThat(data.getBillNumber()).isEqualTo("KM-20261023-000101");
  }

  @Test
  @DisplayName("Regenerate KHQR creates a fresh payment with different MD5")
  void testRegenerateKhqr() {
    Order order =
        Order.builder()
            .id(102L)
            .orderNumber("KM-20261023-000102")
            .userId(1L)
            .status(OrderStatus.PENDING_PAYMENT)
            .paymentStatus(PaymentStatus.PENDING)
            .paymentMethod(PaymentMethod.KHQR)
            .subtotalCents(1000L)
            .discountCents(0L)
            .shippingCents(0L)
            .totalCents(1000L)
            .currency("USD")
            .placedAt(Instant.now())
            .build();

    Payment oldPayment =
        Payment.builder()
            .id(10L)
            .orderId(102L)
            .method(PaymentMethod.KHQR)
            .status(PaymentStatus.PENDING)
            .amountCents(1000L)
            .khqrMd5("old-md5-value-1234567890abcdef12")
            .qrExpiresAt(Instant.now().minus(Duration.ofMinutes(5)))
            .build();

    when(orderRepository.findByOrderNumber("KM-20261023-000102")).thenReturn(Optional.of(order));
    when(paymentRepository.findByOrderIdOrderByIdDesc(102L)).thenReturn(List.of(oldPayment));
    when(paymentRepository.save(any(Payment.class)))
        .thenAnswer(
            inv -> {
              Payment p = inv.getArgument(0);
              if (p.getId() == null) {
                p.setId(11L);
              }
              return p;
            });
    when(jwtService.generateStreamToken(any(), any())).thenReturn("mock-stream-token");

    KhqrPaymentDto regenerated = khqrService.regenerateKhqr(1L, "KM-20261023-000102");

    assertThat(regenerated).isNotNull();
    assertThat(oldPayment.getStatus()).isEqualTo(PaymentStatus.EXPIRED);
    assertThat(regenerated.paymentId()).isEqualTo(11L);
    assertThat(regenerated.md5()).isNotEqualTo("old-md5-value-1234567890abcdef12");
  }

  @Test
  @DisplayName("simulate-paid in LIVE mode returns 403 FORBIDDEN")
  void testSimulatePaidInLiveModeReturnsForbidden() {
    AppProperty liveProperty =
        new AppProperty(
            "http://localhost:3000",
            "http://localhost:4000/api",
            "uploads",
            new AppProperty.Jwt(
                "secret123456789012345678901234567890",
                "secret123456789012345678901234567890",
                Duration.ofMinutes(15),
                Duration.ofDays(7)),
            new AppProperty.Mail("no-reply@khmermart.com"),
            null,
            new AppProperty.Khqr(
                "LIVE",
                "INDIVIDUAL",
                "https://api-bakong.nbc.gov.kh",
                "token",
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

    KhqrServiceImpl liveKhqrService =
        new KhqrServiceImpl(
            liveProperty,
            orderRepository,
            paymentRepository,
            paymentConfirmationService,
            jwtService,
            mock(
                SettingService.class,
                inv -> inv.getMethod().getName().equals("getKhqrMode") ? "LIVE" : null),
            mock(KhqrPaymentChecker.class));

    org.assertj.core.api.Assertions.assertThatThrownBy(
            () -> liveKhqrService.simulatePaid(1L, 100L, false))
        .isInstanceOf(com.khmermart.common.exception.CustomException.class)
        .hasFieldOrPropertyWithValue(
            "errorCode", com.khmermart.common.exception.ErrorCode.FORBIDDEN);
  }
}
