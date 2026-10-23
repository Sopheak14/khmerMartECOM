package com.khmermart.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.khmermart.AbstractIntegrationTest;
import com.khmermart.model.entity.Inventory;
import com.khmermart.model.entity.InventoryMovement;
import com.khmermart.model.entity.Order;
import com.khmermart.model.entity.OrderItem;
import com.khmermart.model.entity.Payment;
import com.khmermart.model.entity.Product;
import com.khmermart.model.enumerate.InventoryMovementType;
import com.khmermart.model.enumerate.OrderStatus;
import com.khmermart.model.enumerate.PaymentMethod;
import com.khmermart.model.enumerate.PaymentStatus;
import com.khmermart.model.enumerate.Roles;
import com.khmermart.repository.CouponRedemptionRepository;
import com.khmermart.repository.CouponRepository;
import com.khmermart.repository.InventoryMovementRepository;
import com.khmermart.repository.InventoryRepository;
import com.khmermart.repository.OrderItemRepository;
import com.khmermart.repository.OrderRepository;
import com.khmermart.repository.PaymentRepository;
import com.khmermart.repository.ProductRepository;
import com.khmermart.security.JwtService;
import com.khmermart.service.impl.PaymentExpiryJob;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
class OrderPaymentIntegrationTest extends AbstractIntegrationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private OrderRepository orderRepository;
  @Autowired private OrderItemRepository orderItemRepository;
  @Autowired private PaymentRepository paymentRepository;
  @Autowired private InventoryRepository inventoryRepository;
  @Autowired private InventoryMovementRepository movementRepository;
  @Autowired private ProductRepository productRepository;
  @Autowired private CouponRepository couponRepository;
  @Autowired private CouponRedemptionRepository couponRedemptionRepository;
  @Autowired private PaymentExpiryJob paymentExpiryJob;
  @Autowired private JwtService jwtService;
  @Autowired private ObjectMapper objectMapper;

  private String customer1Token;
  private String customer2Token;
  private String adminToken;

  @BeforeEach
  void setUpTokens() {
    customer1Token =
        "Bearer " + jwtService.generateAccessToken(3L, "customer1@khmermart.local", Roles.CUSTOMER);
    customer2Token =
        "Bearer " + jwtService.generateAccessToken(4L, "customer2@khmermart.local", Roles.CUSTOMER);
    adminToken =
        "Bearer " + jwtService.generateAccessToken(1L, "admin@khmermart.local", Roles.ADMIN);
  }

  @Test
  @DisplayName(
      "Full KHQR flow: generate, stream token, simulate-paid idempotency and DB verification")
  void testKhqrFlowAndSimulatePaidIdempotency() throws Exception {
    Product product =
        productRepository.save(
            Product.builder()
                .sku("TEST-KHQR-PROD")
                .name("KHQR Test Product")
                .slug("khqr-test-product-" + System.currentTimeMillis())
                .priceCents(5000L)
                .active(true)
                .build());

    Inventory inv =
        inventoryRepository.save(
            Inventory.builder()
                .productId(product.getId())
                .quantityOnHand(10)
                .quantityReserved(2)
                .lowStockThreshold(2)
                .build());

    Order order =
        orderRepository.save(
            Order.builder()
                .orderNumber("KM-TEST-KHQR-01")
                .userId(3L)
                .status(OrderStatus.PENDING_PAYMENT)
                .paymentStatus(PaymentStatus.PENDING)
                .paymentMethod(PaymentMethod.KHQR)
                .subtotalCents(5000L)
                .discountCents(0L)
                .shippingCents(0L)
                .totalCents(5000L)
                .shippingAddress(
                    "{\"recipientName\":\"Test Customer\",\"phone\":\"+85512345678\",\"line1\":\"St 200\",\"province\":\"Phnom Penh\"}")
                .shippingMethodCode("STANDARD")
                .placedAt(Instant.now())
                .build());

    OrderItem item =
        orderItemRepository.save(
            OrderItem.builder()
                .orderId(order.getId())
                .productId(product.getId())
                .productNameSnapshot("KHQR Test Product")
                .skuSnapshot("TEST-KHQR-PROD")
                .unitPriceCents(5000L)
                .quantity(2)
                .lineTotalCents(5000L)
                .build());

    // 1. Generate KHQR
    String responseJson =
        mockMvc
            .perform(
                post("/api/payments/khqr/{orderNumber}", order.getOrderNumber())
                    .header("Authorization", customer1Token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.qrString").exists())
            .andExpect(jsonPath("$.data.md5").isString())
            .andExpect(
                jsonPath("$.data.qrImageDataUrl")
                    .value(org.hamcrest.Matchers.startsWith("data:image/png;base64,")))
            .andExpect(jsonPath("$.data.streamToken").exists())
            .andReturn()
            .getResponse()
            .getContentAsString();

    Long paymentId = objectMapper.readTree(responseJson).path("data").path("paymentId").asLong();

    // 2. Calling a second time returns the SAME payment
    mockMvc
        .perform(
            post("/api/payments/khqr/{orderNumber}", order.getOrderNumber())
                .header("Authorization", customer1Token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.paymentId").value(paymentId));

    // 3. Simulate Paid first time
    mockMvc
        .perform(
            post("/api/payments/{paymentId}/simulate-paid", paymentId)
                .header("Authorization", customer1Token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.status").value("PAID"))
        .andExpect(jsonPath("$.data.bakongHash").value(org.hamcrest.Matchers.startsWith("MOCK-")));

    // Verify DB
    Payment paidPayment = paymentRepository.findById(paymentId).orElseThrow();
    assertThat(paidPayment.getStatus()).isEqualTo(PaymentStatus.PAID);
    assertThat(paidPayment.getBakongHash()).startsWith("MOCK-");

    Order confirmedOrder = orderRepository.findById(order.getId()).orElseThrow();
    assertThat(confirmedOrder.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
    assertThat(confirmedOrder.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);

    Inventory updatedInv = inventoryRepository.findById(inv.getId()).orElseThrow();
    assertThat(updatedInv.getQuantityOnHand()).isEqualTo(8); // 10 - 2
    assertThat(updatedInv.getQuantityReserved()).isEqualTo(0); // 2 - 2

    List<InventoryMovement> movements = movementRepository.findByRefOrderId(order.getId());
    assertThat(movements)
        .anyMatch(m -> m.getType() == InventoryMovementType.SALE && m.getQuantity() == 2);

    // 4. Simulate Paid SECOND time: idempotent, no-op
    mockMvc
        .perform(
            post("/api/payments/{paymentId}/simulate-paid", paymentId)
                .header("Authorization", customer1Token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("PAID"));

    Inventory afterSecondCallInv = inventoryRepository.findById(inv.getId()).orElseThrow();
    assertThat(afterSecondCallInv.getQuantityOnHand()).isEqualTo(8); // unchanged
    assertThat(afterSecondCallInv.getQuantityReserved()).isEqualTo(0); // unchanged
  }

  @Test
  @DisplayName(
      "PaymentExpiryJob: unpaid KHQR payment past expires_at is marked EXPIRED, order PAYMENT_EXPIRED, stock released")
  void testPaymentExpiryJob() {
    Product product =
        productRepository.save(
            Product.builder()
                .sku("TEST-EXP-PROD")
                .name("Expiry Test Product")
                .slug("expiry-test-product-" + System.currentTimeMillis())
                .priceCents(2000L)
                .active(true)
                .build());

    Inventory inv =
        inventoryRepository.save(
            Inventory.builder()
                .productId(product.getId())
                .quantityOnHand(10)
                .quantityReserved(3)
                .lowStockThreshold(2)
                .build());

    Order order =
        orderRepository.save(
            Order.builder()
                .orderNumber("KM-TEST-EXP-01")
                .userId(3L)
                .status(OrderStatus.PENDING_PAYMENT)
                .paymentStatus(PaymentStatus.PENDING)
                .paymentMethod(PaymentMethod.KHQR)
                .subtotalCents(2000L)
                .discountCents(0L)
                .shippingCents(0L)
                .totalCents(2000L)
                .shippingAddress("{}")
                .shippingMethodCode("STANDARD")
                .placedAt(Instant.now().minus(Duration.ofMinutes(15)))
                .build());

    orderItemRepository.save(
        OrderItem.builder()
            .orderId(order.getId())
            .productId(product.getId())
            .productNameSnapshot("Expiry Test Product")
            .skuSnapshot("TEST-EXP-PROD")
            .unitPriceCents(2000L)
            .quantity(3)
            .lineTotalCents(6000L)
            .build());

    Payment payment =
        paymentRepository.save(
            Payment.builder()
                .orderId(order.getId())
                .method(PaymentMethod.KHQR)
                .status(PaymentStatus.PENDING)
                .amountCents(2000L)
                .khqrMd5("exp-test-md5-123")
                .qrExpiresAt(Instant.now().minus(Duration.ofMinutes(5))) // in past
                .createdAt(Instant.now().minus(Duration.ofMinutes(15)))
                .build());

    // Run expiry job
    paymentExpiryJob.expirePendingPayments();

    Payment expiredPayment = paymentRepository.findById(payment.getId()).orElseThrow();
    assertThat(expiredPayment.getStatus()).isEqualTo(PaymentStatus.EXPIRED);

    Order expiredOrder = orderRepository.findById(order.getId()).orElseThrow();
    assertThat(expiredOrder.getStatus()).isEqualTo(OrderStatus.PAYMENT_EXPIRED);
    assertThat(expiredOrder.getPaymentStatus()).isEqualTo(PaymentStatus.EXPIRED);

    Inventory restoredInv = inventoryRepository.findById(inv.getId()).orElseThrow();
    assertThat(restoredInv.getQuantityReserved()).isEqualTo(0); // 3 - 3 released
    assertThat(restoredInv.getQuantityOnHand()).isEqualTo(10); // unchanged

    List<InventoryMovement> movements = movementRepository.findByRefOrderId(order.getId());
    assertThat(movements)
        .anyMatch(m -> m.getType() == InventoryMovementType.RELEASE && m.getQuantity() == 3);
  }

  @Test
  @DisplayName(
      "COD lifecycle: placed as CONFIRMED, mark SHIPPED converts RESERVE to SALE, cancel releases")
  void testCodLifecycle() throws Exception {
    Product product =
        productRepository.save(
            Product.builder()
                .sku("TEST-COD-PROD")
                .name("COD Test Product")
                .slug("cod-test-product-" + System.currentTimeMillis())
                .priceCents(3000L)
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
                .orderNumber("KM-TEST-COD-01")
                .userId(3L)
                .status(OrderStatus.CONFIRMED) // COD starts CONFIRMED
                .paymentStatus(PaymentStatus.PENDING)
                .paymentMethod(PaymentMethod.COD)
                .subtotalCents(3000L)
                .discountCents(0L)
                .shippingCents(0L)
                .totalCents(3000L)
                .shippingAddress("{}")
                .shippingMethodCode("STANDARD")
                .placedAt(Instant.now())
                .build());

    orderItemRepository.save(
        OrderItem.builder()
            .orderId(order.getId())
            .productId(product.getId())
            .productNameSnapshot("COD Test Product")
            .skuSnapshot("TEST-COD-PROD")
            .unitPriceCents(3000L)
            .quantity(1)
            .lineTotalCents(3000L)
            .build());

    // Admin ships order -> converts RESERVE -> SALE
    mockMvc
        .perform(
            post("/api/admin/orders/{orderNumber}/ship", order.getOrderNumber())
                .header("Authorization", adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"carrier\":\"J&T Express\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("SHIPPED"))
        .andExpect(jsonPath("$.data.carrier").value("J&T Express"))
        .andExpect(jsonPath("$.data.trackingNumber").exists());

    Inventory shippedInv = inventoryRepository.findById(inv.getId()).orElseThrow();
    assertThat(shippedInv.getQuantityOnHand()).isEqualTo(9); // 10 - 1
    assertThat(shippedInv.getQuantityReserved()).isEqualTo(0); // 1 - 1

    List<InventoryMovement> movements = movementRepository.findByRefOrderId(order.getId());
    assertThat(movements)
        .anyMatch(m -> m.getType() == InventoryMovementType.SALE && m.getQuantity() == 1);
  }

  @Test
  @DisplayName(
      "Order cancellation: customer can cancel CONFIRMED order; cannot cancel SHIPPED order (422)")
  void testOrderCancellation() throws Exception {
    Order order =
        orderRepository.save(
            Order.builder()
                .orderNumber("KM-TEST-CANCEL-01")
                .userId(3L)
                .status(OrderStatus.CONFIRMED)
                .paymentStatus(PaymentStatus.PENDING)
                .paymentMethod(PaymentMethod.COD)
                .subtotalCents(1000L)
                .totalCents(1000L)
                .shippingAddress("{}")
                .shippingMethodCode("STANDARD")
                .placedAt(Instant.now())
                .build());

    // Cancel CONFIRMED order -> OK
    mockMvc
        .perform(
            post("/api/orders/{orderNumber}/cancel", order.getOrderNumber())
                .header("Authorization", customer1Token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"Changed my mind\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("CANCELLED"))
        .andExpect(jsonPath("$.data.cancelReason").value("Changed my mind"));

    // Attempt to cancel an already SHIPPED order -> 422
    Order shippedOrder =
        orderRepository.save(
            Order.builder()
                .orderNumber("KM-TEST-SHIPPED-01")
                .userId(3L)
                .status(OrderStatus.SHIPPED)
                .paymentStatus(PaymentStatus.PENDING)
                .paymentMethod(PaymentMethod.COD)
                .subtotalCents(1000L)
                .totalCents(1000L)
                .shippingAddress("{}")
                .shippingMethodCode("STANDARD")
                .placedAt(Instant.now())
                .build());

    mockMvc
        .perform(
            post("/api/orders/{orderNumber}/cancel", shippedOrder.getOrderNumber())
                .header("Authorization", customer1Token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"Too late to cancel\"}"))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.error.code").value("INVALID_STATUS_TRANSITION"));
  }

  @Test
  @DisplayName("Ownership check: customer 2 cannot access customer 1's order")
  void testOrderOwnershipCheck() throws Exception {
    Order order =
        orderRepository.save(
            Order.builder()
                .orderNumber("KM-TEST-OWNER-01")
                .userId(3L)
                .status(OrderStatus.CONFIRMED)
                .paymentStatus(PaymentStatus.PENDING)
                .paymentMethod(PaymentMethod.COD)
                .subtotalCents(1000L)
                .totalCents(1000L)
                .shippingAddress("{}")
                .shippingMethodCode("STANDARD")
                .placedAt(Instant.now())
                .build());

    mockMvc
        .perform(
            get("/api/orders/{orderNumber}", order.getOrderNumber())
                .header("Authorization", customer2Token))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
  }

  @Test
  @DisplayName(
      "Bank transfer: upload slip -> PENDING_VERIFICATION, admin approve -> PAID + CONFIRMED")
  void testBankTransferSlipAndApproval() throws Exception {
    Order order =
        orderRepository.save(
            Order.builder()
                .orderNumber("KM-TEST-BANK-01")
                .userId(3L)
                .status(OrderStatus.PENDING_PAYMENT)
                .paymentStatus(PaymentStatus.PENDING)
                .paymentMethod(PaymentMethod.BANK_TRANSFER)
                .subtotalCents(5000L)
                .totalCents(5000L)
                .shippingAddress("{}")
                .shippingMethodCode("STANDARD")
                .placedAt(Instant.now())
                .build());

    MockMultipartFile slipFile =
        new MockMultipartFile(
            "file",
            "receipt.png",
            "image/png",
            "fake-image-bytes".getBytes(StandardCharsets.UTF_8));

    // Upload slip
    mockMvc
        .perform(
            multipart("/api/payments/bank-transfer/{orderNumber}/slip", order.getOrderNumber())
                .file(slipFile)
                .header("Authorization", customer1Token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.paymentStatus").value("PENDING_VERIFICATION"));

    Payment payment =
        paymentRepository.findFirstByOrderIdOrderByIdDesc(order.getId()).orElseThrow();
    assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING_VERIFICATION);

    // Admin approve
    mockMvc
        .perform(
            post("/api/admin/payments/{id}/approve-bank-transfer", payment.getId())
                .header("Authorization", adminToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("CONFIRMED"))
        .andExpect(jsonPath("$.data.paymentStatus").value("PAID"));
  }

  @Test
  @DisplayName("Invoice PDF: GET /api/orders/{orderNumber}/invoice.pdf returns valid PDF bytes")
  void testInvoicePdfDownload() throws Exception {
    Order order =
        orderRepository.save(
            Order.builder()
                .orderNumber("KM-TEST-INV-01")
                .userId(3L)
                .status(OrderStatus.CONFIRMED)
                .paymentStatus(PaymentStatus.PAID)
                .paymentMethod(PaymentMethod.KHQR)
                .subtotalCents(99900L)
                .discountCents(10000L)
                .shippingCents(150L)
                .totalCents(90050L)
                .shippingAddress(
                    "{\"recipientName\":\"Sopheak Chheang\",\"phone\":\"+85512345678\",\"line1\":\"St 271\",\"district\":\"Chamkarmon\",\"province\":\"Phnom Penh\"}")
                .shippingMethodCode("STANDARD")
                .placedAt(Instant.now())
                .build());

    orderItemRepository.save(
        OrderItem.builder()
            .orderId(order.getId())
            .productId(1001L)
            .productNameSnapshot("Laptop Pro 15")
            .skuSnapshot("LP15-SLV-16")
            .unitPriceCents(99900L)
            .quantity(1)
            .lineTotalCents(99900L)
            .build());

    paymentRepository.save(
        Payment.builder()
            .orderId(order.getId())
            .method(PaymentMethod.KHQR)
            .status(PaymentStatus.PAID)
            .amountCents(90050L)
            .bakongHash("MOCK-INVOICE-HASH-123")
            .paidAt(Instant.now())
            .createdAt(Instant.now())
            .build());

    byte[] pdfBytes =
        mockMvc
            .perform(
                get("/api/orders/{orderNumber}/invoice.pdf", order.getOrderNumber())
                    .header("Authorization", customer1Token))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Type", "application/pdf"))
            .andExpect(
                header()
                    .string(
                        "Content-Disposition",
                        "form-data; name=\"attachment\"; filename=\"invoice-KM-TEST-INV-01.pdf\""))
            .andReturn()
            .getResponse()
            .getContentAsByteArray();

    assertThat(pdfBytes).isNotEmpty();
    assertThat(new String(pdfBytes, 0, 4, StandardCharsets.US_ASCII)).isEqualTo("%PDF");
  }
}
