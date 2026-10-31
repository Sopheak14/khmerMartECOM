package com.khmermart.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.khmermart.AbstractIntegrationTest;
import com.khmermart.model.entity.Inventory;
import com.khmermart.model.entity.Order;
import com.khmermart.model.entity.OrderItem;
import com.khmermart.model.entity.Payment;
import com.khmermart.model.entity.Product;
import com.khmermart.model.entity.Setting;
import com.khmermart.model.enumerate.OrderStatus;
import com.khmermart.model.enumerate.PaymentMethod;
import com.khmermart.model.enumerate.PaymentStatus;
import com.khmermart.model.response.BakongCheckResponse;
import com.khmermart.model.response.BakongTxData;
import com.khmermart.model.response.PaymentCheckResultDto;
import com.khmermart.repository.InventoryRepository;
import com.khmermart.repository.OrderItemRepository;
import com.khmermart.repository.OrderRepository;
import com.khmermart.repository.PaymentRepository;
import com.khmermart.repository.ProductRepository;
import com.khmermart.repository.SettingRepository;
import com.khmermart.service.AdminPaymentService;
import com.khmermart.service.BakongApiClient;
import com.khmermart.service.impl.KhqrPollingJob;
import com.khmermart.service.impl.PaymentExpiryJob;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;

/** Real DB, mocked Bakong: expiry -> late payment, and runtime mode from Settings. */
class LatePaymentIntegrationTest extends AbstractIntegrationTest {

  @MockBean private BakongApiClient bakongApiClient;

  @Autowired private OrderRepository orderRepository;
  @Autowired private OrderItemRepository orderItemRepository;
  @Autowired private PaymentRepository paymentRepository;
  @Autowired private InventoryRepository inventoryRepository;
  @Autowired private ProductRepository productRepository;
  @Autowired private SettingRepository settingRepository;
  @Autowired private PaymentExpiryJob paymentExpiryJob;
  @Autowired private KhqrPollingJob khqrPollingJob;
  @Autowired private AdminPaymentService adminPaymentService;
  @Autowired private com.khmermart.config.AppProperty appProperty;

  @AfterEach
  void resetMode() {
    settingRepository.deleteById("KHQR_MODE");
  }

  private BakongCheckResponse paid(Payment p) {
    return new BakongCheckResponse(
        0,
        "Success",
        null,
        new BakongTxData(
            "LATE-HASH-" + p.getId(),
            "customer@abaa",
            appProperty.khqr().accountId(),
            "USD",
            BigDecimal.valueOf(p.getAmountCents()).movePointLeft(2),
            null,
            1L,
            2L,
            "EXT-REF-" + p.getId()));
  }

  private Payment[] seed(String suffix) {
    Product product =
        productRepository.save(
            Product.builder()
                .sku("LATE-" + suffix)
                .name("Late Product")
                .slug("late-product-" + suffix + System.nanoTime())
                .priceCents(159L)
                .active(true)
                .build());
    Inventory inv =
        inventoryRepository.save(
            Inventory.builder()
                .productId(product.getId())
                .quantityOnHand(10)
                .quantityReserved(1)
                .lowStockThreshold(2)
                .build());
    Order order =
        orderRepository.save(
            Order.builder()
                .orderNumber("KM-LATE-" + suffix)
                .userId(3L)
                .status(OrderStatus.PENDING_PAYMENT)
                .paymentStatus(PaymentStatus.PENDING)
                .paymentMethod(PaymentMethod.KHQR)
                .subtotalCents(159L)
                .shippingCents(0L)
                .totalCents(159L)
                .shippingAddress("{}")
                .shippingMethodCode("STANDARD")
                .placedAt(Instant.now().minus(Duration.ofMinutes(15)))
                .build());
    orderItemRepository.save(
        OrderItem.builder()
            .orderId(order.getId())
            .productId(product.getId())
            .productNameSnapshot("Late Product")
            .skuSnapshot("LATE-" + suffix)
            .unitPriceCents(159L)
            .quantity(1)
            .lineTotalCents(159L)
            .build());
    Payment payment =
        paymentRepository.save(
            Payment.builder()
                .orderId(order.getId())
                .method(PaymentMethod.KHQR)
                .status(PaymentStatus.PENDING)
                .amountCents(159L)
                .khqrMd5("late-md5-" + suffix)
                .qrExpiresAt(Instant.now().minus(Duration.ofMinutes(5)))
                .createdAt(Instant.now().minus(Duration.ofMinutes(15)))
                .build());
    return new Payment[] {payment};
  }

  @Test
  void expiredThenPaidIsRecordedAsLatePaymentAndOrderFlagged() {
    Payment payment = seed("A")[0];
    // mode switched to LIVE at runtime via Settings (env/startup mode stays MOCK)
    settingRepository.save(new Setting("KHQR_MODE", "LIVE"));

    // Bakong says "not found" while the expiry job runs -> payment/order expire, stock released
    when(bakongApiClient.checkTransactionByMd5(any()))
        .thenReturn(new BakongCheckResponse(1, "not found", 3, null));
    paymentExpiryJob.expirePendingPayments();
    Payment expired = paymentRepository.findById(payment.getId()).orElseThrow();
    assertThat(expired.getStatus()).isEqualTo(PaymentStatus.EXPIRED);
    assertThat(expired.getCheckCount()).isEqualTo(1); // expiry job's final check was counted
    Order order = orderRepository.findById(payment.getOrderId()).orElseThrow();
    assertThat(order.getStatus()).isEqualTo(OrderStatus.PAYMENT_EXPIRED);

    // The customer actually paid. Admin re-check records it.
    when(bakongApiClient.checkTransactionByMd5(any())).thenReturn(paid(payment));
    PaymentCheckResultDto res =
        adminPaymentService.recheckPayment(payment.getId(), 1L, "127.0.0.1");

    assertThat(res.outcome()).isEqualTo(PaymentCheckResultDto.Outcome.LATE_PAID);
    Payment after = paymentRepository.findById(payment.getId()).orElseThrow();
    assertThat(after.getStatus()).isEqualTo(PaymentStatus.PAID);
    assertThat(after.getBakongHash()).isEqualTo("LATE-HASH-" + payment.getId());
    assertThat(after.getFromAccountId()).isEqualTo("customer@abaa");
    assertThat(after.getExternalRef()).isEqualTo("EXT-REF-" + payment.getId());
    assertThat(after.getCheckCount()).isEqualTo(2);
    Order flagged = orderRepository.findById(payment.getOrderId()).orElseThrow();
    assertThat(flagged.isNeedsAttention()).isTrue();
    assertThat(flagged.getAttentionNote()).contains("LATE PAYMENT");
    assertThat(flagged.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
    assertThat(flagged.getStatus()).isEqualTo(OrderStatus.PAYMENT_EXPIRED); // admin decides
  }

  @Test
  void pollerFollowsRuntimeModeFromSettings() {
    Payment payment = seed("B")[0];
    payment.setQrExpiresAt(Instant.now().plus(Duration.ofMinutes(5)));
    paymentRepository.save(payment);
    when(bakongApiClient.checkTransactionByMd5(any()))
        .thenReturn(new BakongCheckResponse(1, "not found", 3, null));

    // No Settings row, startup env is MOCK -> poller must not touch this payment
    khqrPollingJob.pollPendingPayments();
    assertThat(paymentRepository.findById(payment.getId()).orElseThrow().getCheckCount()).isZero();

    settingRepository.save(new Setting("KHQR_MODE", "LIVE"));
    khqrPollingJob.pollPendingPayments();
    Payment checked = paymentRepository.findById(payment.getId()).orElseThrow();
    assertThat(checked.getCheckCount()).isEqualTo(1);
    assertThat(checked.getLastCheckedAt()).isNotNull();
  }
}
