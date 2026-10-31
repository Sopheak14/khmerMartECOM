package com.khmermart.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.khmermart.AbstractIntegrationTest;
import com.khmermart.model.entity.Order;
import com.khmermart.model.entity.Payment;
import com.khmermart.model.entity.Product;
import com.khmermart.model.enumerate.OrderStatus;
import com.khmermart.model.enumerate.PaymentMethod;
import com.khmermart.model.enumerate.PaymentStatus;
import com.khmermart.model.enumerate.Roles;
import com.khmermart.repository.OrderRepository;
import com.khmermart.repository.PaymentRepository;
import com.khmermart.repository.ProductRepository;
import com.khmermart.security.JwtService;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

/** Regression tests for BACKEND-FIX-1 (payment access control, expiry, deadline, price unit). */
@AutoConfigureMockMvc
class BackendFix1IntegrationTest extends AbstractIntegrationTest {

  private static final AtomicInteger SEQ = new AtomicInteger();

  @Autowired private MockMvc mockMvc;
  @Autowired private OrderRepository orderRepository;
  @Autowired private PaymentRepository paymentRepository;
  @Autowired private ProductRepository productRepository;
  @Autowired private JwtService jwtService;
  @Autowired private ObjectMapper objectMapper;

  private String owner; // customer1 (id 3)
  private String other; // customer2 (id 4)
  private String staff;
  private String admin;

  @BeforeEach
  void tokens() {
    owner =
        "Bearer " + jwtService.generateAccessToken(3L, "customer1@khmermart.local", Roles.CUSTOMER);
    other =
        "Bearer " + jwtService.generateAccessToken(4L, "customer2@khmermart.local", Roles.CUSTOMER);
    staff = "Bearer " + jwtService.generateAccessToken(2L, "staff@khmermart.local", Roles.STAFF);
    admin = "Bearer " + jwtService.generateAccessToken(1L, "admin@khmermart.local", Roles.ADMIN);
  }

  private Order pendingOrder() {
    return orderRepository.save(
        Order.builder()
            .orderNumber("KM-FIX1-" + SEQ.incrementAndGet() + "-" + System.nanoTime())
            .userId(3L)
            .status(OrderStatus.PENDING_PAYMENT)
            .paymentStatus(PaymentStatus.PENDING)
            .paymentMethod(PaymentMethod.KHQR)
            .subtotalCents(5000L)
            .discountCents(0L)
            .shippingCents(0L)
            .totalCents(5000L)
            .shippingAddress("{}")
            .shippingMethodCode("STANDARD")
            .placedAt(Instant.now())
            .build());
  }

  private long khqrPaymentId(Order order) throws Exception {
    String json =
        mockMvc
            .perform(
                post("/api/payments/khqr/{n}", order.getOrderNumber())
                    .header("Authorization", owner))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return objectMapper.readTree(json).path("data").path("paymentId").asLong();
  }

  @Test
  @DisplayName("Fix1: payment status is visible to owner/staff/admin only; others 404; anon 401")
  void paymentStatusAccessControl() throws Exception {
    long pid = khqrPaymentId(pendingOrder());

    mockMvc
        .perform(get("/api/payments/{id}/status", pid).header("Authorization", owner))
        .andExpect(status().isOk());
    mockMvc
        .perform(get("/api/payments/{id}/status", pid).header("Authorization", staff))
        .andExpect(status().isOk());
    mockMvc
        .perform(get("/api/payments/{id}/status", pid).header("Authorization", admin))
        .andExpect(status().isOk());
    mockMvc
        .perform(get("/api/payments/{id}/status", pid).header("Authorization", other))
        .andExpect(status().isNotFound());
    mockMvc.perform(get("/api/payments/{id}/status", pid)).andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("Fix1: SSE stream answers only the owner (token) or staff/admin; others 404/401")
  void paymentStreamAccessControl() throws Exception {
    long pid = khqrPaymentId(pendingOrder());

    // another user's (validly signed) stream token for this payment -> 404
    String otherToken = jwtService.generateStreamToken(pid, 4L);
    mockMvc
        .perform(get("/api/payments/{id}/stream", pid).param("token", otherToken))
        .andExpect(status().isNotFound());
    // authenticated non-owner -> 404
    mockMvc
        .perform(get("/api/payments/{id}/stream", pid).header("Authorization", other))
        .andExpect(status().isNotFound());
    // no / garbage token, no auth -> 401
    mockMvc.perform(get("/api/payments/{id}/stream", pid)).andExpect(status().isUnauthorized());
    mockMvc
        .perform(get("/api/payments/{id}/stream", pid).param("token", "garbage"))
        .andExpect(status().isUnauthorized());
    // token issued to the owner -> stream opens
    String ownerToken = jwtService.generateStreamToken(pid, 3L);
    mockMvc
        .perform(get("/api/payments/{id}/stream", pid).param("token", ownerToken))
        .andExpect(request().asyncStarted());
    // staff via bearer -> stream opens
    mockMvc
        .perform(get("/api/payments/{id}/stream", pid).header("Authorization", staff))
        .andExpect(request().asyncStarted());
  }

  @Test
  @DisplayName("Fix2: simulate-paid on an EXPIRED payment returns 422 INVALID_STATUS_TRANSITION")
  void simulatePaidOnExpiredPayment() throws Exception {
    long pid = khqrPaymentId(pendingOrder());
    Payment p = paymentRepository.findById(pid).orElseThrow();
    p.setStatus(PaymentStatus.EXPIRED);
    paymentRepository.save(p);

    mockMvc
        .perform(post("/api/payments/{id}/simulate-paid", pid).header("Authorization", owner))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.error.code").value("INVALID_STATUS_TRANSITION"));

    assertThat(paymentRepository.findById(pid).orElseThrow().getStatus())
        .isEqualTo(PaymentStatus.EXPIRED);
  }

  @Test
  @DisplayName("Fix3: order.paymentDeadline equals the KHQR qrExpiresAt")
  void paymentDeadlineEqualsKhqrExpiry() throws Exception {
    Order order = pendingOrder();
    long pid = khqrPaymentId(order);

    Payment p = paymentRepository.findById(pid).orElseThrow();
    Order reloaded = orderRepository.findById(order.getId()).orElseThrow();
    assertThat(reloaded.getPaymentDeadline()).isEqualTo(p.getQrExpiresAt());
  }

  @Test
  @DisplayName("Fix4: minPrice/maxPrice are always whole dollars (no cents heuristic)")
  void priceFilterIsAlwaysDollars() throws Exception {
    String brand = "FixBrand" + System.nanoTime();
    productRepository.save(
        Product.builder()
            .sku("FIX4-" + System.nanoTime())
            .name("Fix4 Product")
            .slug("fix4-product-" + System.nanoTime())
            .brand(brand)
            .priceCents(12000L) // $120.00
            .active(true)
            .build());

    // $100..$150 contains it
    mockMvc
        .perform(
            get("/api/products")
                .param("brand", brand)
                .param("minPrice", "100")
                .param("maxPrice", "150"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.length()").value(1));
    // 12000 means $12,000 (the old heuristic read it as 12000 cents = $120 and matched)
    mockMvc
        .perform(get("/api/products").param("brand", brand).param("minPrice", "12000"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.length()").value(0));
    // maxPrice=12000 ($12,000) must include it; old heuristic: 12000 cents = $120 also included,
    // so use a value that distinguishes: maxPrice=10000 -> $10,000 includes, old = $100 excludes
    mockMvc
        .perform(get("/api/products").param("brand", brand).param("maxPrice", "10000"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.length()").value(1));
  }
}
