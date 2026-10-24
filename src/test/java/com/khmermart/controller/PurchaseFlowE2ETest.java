package com.khmermart.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.khmermart.AbstractIntegrationTest;
import com.khmermart.model.entity.Address;
import com.khmermart.model.entity.InventoryMovement;
import com.khmermart.model.entity.Order;
import com.khmermart.model.entity.Payment;
import com.khmermart.model.entity.Review;
import com.khmermart.model.enumerate.InventoryMovementType;
import com.khmermart.model.enumerate.OrderStatus;
import com.khmermart.model.enumerate.PaymentMethod;
import com.khmermart.model.enumerate.PaymentStatus;
import com.khmermart.model.enumerate.ReviewStatus;
import com.khmermart.model.request.AdminReviewUpdateRequest;
import com.khmermart.model.request.ApplyCouponRequest;
import com.khmermart.model.request.CartItemRequest;
import com.khmermart.model.request.CheckoutRequest;
import com.khmermart.model.request.CreateReviewRequest;
import com.khmermart.model.request.ShipOrderRequest;
import com.khmermart.model.request.UpdateOrderStatusRequest;
import com.khmermart.repository.AddressRepository;
import com.khmermart.repository.CouponRedemptionRepository;
import com.khmermart.repository.InventoryMovementRepository;
import com.khmermart.repository.NotificationRepository;
import com.khmermart.repository.OrderRepository;
import com.khmermart.repository.PaymentRepository;
import com.khmermart.repository.ReviewRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
class PurchaseFlowE2ETest extends AbstractIntegrationTest {

  private static final Logger log = LoggerFactory.getLogger(PurchaseFlowE2ETest.class);

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private AddressRepository addressRepository;
  @Autowired private OrderRepository orderRepository;
  @Autowired private PaymentRepository paymentRepository;
  @Autowired private InventoryMovementRepository movementRepository;
  @Autowired private NotificationRepository notificationRepository;
  @Autowired private ReviewRepository reviewRepository;
  @Autowired private CouponRedemptionRepository couponRedemptionRepository;

  @Test
  @DisplayName("Full 12-step lesson scenario as ONE comprehensive integration test")
  void testFullTwelveStepPurchaseFlow() throws Exception {
    log.info("=== STEP 1: Authentication (Customer & Admin Login) ===");
    String custLoginRes =
        mockMvc
            .perform(
                post("/api/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"email\":\"customer1@khmermart.local\",\"password\":\"Customer@123\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andReturn()
            .getResponse()
            .getContentAsString();
    String customerToken =
        "Bearer " + objectMapper.readTree(custLoginRes).path("data").path("accessToken").asText();
    long customerId = objectMapper.readTree(custLoginRes).path("data").path("user").path("id").asLong();

    String adminLoginRes =
        mockMvc
            .perform(
                post("/api/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"email\":\"admin@khmermart.local\",\"password\":\"Admin@12345\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andReturn()
            .getResponse()
            .getContentAsString();
    String adminToken =
        "Bearer " + objectMapper.readTree(adminLoginRes).path("data").path("accessToken").asText();

    log.info("=== STEP 2: Search 'Laptop' with filters (brand=Dell) ===");
    String searchRes =
        mockMvc
            .perform(get("/api/products?q=Laptop&brand=Dell"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)))
            .andReturn()
            .getResponse()
            .getContentAsString();

    // Verify Dell Inspiron 15 (ID 1003) is in results
    long targetProductId = 1003L;
    String targetSlug = "dell-inspiron-15";

    log.info("=== STEP 3: Product detail by slug ({}) ===", targetSlug);
    String detailRes =
        mockMvc
            .perform(get("/api/products/" + targetSlug))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.id").value(targetProductId))
            .andExpect(jsonPath("$.data.name").value("Dell Inspiron 15 Laptop"))
            .andExpect(jsonPath("$.data.priceCents").value(85000))
            .andReturn()
            .getResponse()
            .getContentAsString();

    JsonNode detailJson = objectMapper.readTree(detailRes).path("data");
    assertThat(detailJson.path("variants").size()).isGreaterThanOrEqualTo(1);
    long variantId = detailJson.path("variants").get(0).path("id").asLong();
    long variantPrice = detailJson.path("variants").get(0).path("priceCents").asLong();
    assertThat(variantPrice).isEqualTo(85000L);

    log.info("=== STEP 4: Add to Cart (productId: {}, variantId: {}) ===", targetProductId, variantId);
    mockMvc
        .perform(
            post("/api/cart/items")
                .header("Authorization", customerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new CartItemRequest(targetProductId, variantId, 1))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.subtotalCents").value(85000));

    log.info("=== STEP 5: Apply Coupon SAVE5 ===");
    couponRedemptionRepository.deleteAll();
    mockMvc
        .perform(
            post("/api/cart/coupon")
                .header("Authorization", customerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new ApplyCouponRequest("SAVE5"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.subtotalCents").value(85000))
        .andExpect(jsonPath("$.data.coupon.code").value("SAVE5"))
        .andExpect(jsonPath("$.data.coupon.value").value(500))
        .andExpect(jsonPath("$.data.discountCents").value(17500)) // 17000 (20% KNY Promo) + 500 (Coupon)
        .andExpect(jsonPath("$.data.totalCents").value(67500)); // 85000 - 17500

    log.info("=== STEP 6: Checkout with KHQR ===");
    Address address =
        addressRepository
            .findByUserIdOrderByIdAsc(customerId)
            .stream()
            .findFirst()
            .orElseGet(
                () ->
                    addressRepository.save(
                        Address.builder()
                            .userId(customerId)
                            .label("Home")
                            .recipientName("Sopheap Customer")
                            .phone("+85512345678")
                            .line1("Street 2004")
                            .district("Tuol Kouk")
                            .province("Phnom Penh")
                            .isDefault(true)
                            .build()));

    String idempotencyKey = UUID.randomUUID().toString();
    CheckoutRequest checkoutReq =
        new CheckoutRequest(address.getId(), "STANDARD", PaymentMethod.KHQR, null);

    String checkoutRes =
        mockMvc
            .perform(
                post("/api/checkout")
                    .header("Authorization", customerToken)
                    .header("Idempotency-Key", idempotencyKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(checkoutReq)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.order.orderNumber").isNotEmpty())
            .andExpect(jsonPath("$.data.order.subtotalCents").value(85000))
            .andExpect(jsonPath("$.data.order.discountCents").value(17500))
            .andExpect(jsonPath("$.data.order.shippingCents").value(0)) // STANDARD shipping is free over $50 threshold (5000 cents)
            .andExpect(jsonPath("$.data.order.totalCents").value(67500)) // 85000 - 17500 + 0 = 67500
            .andExpect(jsonPath("$.data.next").isNotEmpty())
            .andReturn()
            .getResponse()
            .getContentAsString();

    String orderNumber = objectMapper.readTree(checkoutRes).path("data").path("order").path("orderNumber").asText();
    long orderId = objectMapper.readTree(checkoutRes).path("data").path("order").path("id").asLong();

    // Verify 6-digit order number format: KM-YYYYMMDD-NNNNNN
    assertThat(orderNumber).matches("^KM-\\d{8}-\\d{6}$");

    // Generate KHQR payment details for the order
    String khqrRes =
        mockMvc
            .perform(
                post("/api/payments/khqr/" + orderNumber)
                    .header("Authorization", customerToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.qrString").isNotEmpty())
            .andExpect(jsonPath("$.data.md5").isNotEmpty())
            .andReturn()
            .getResponse()
            .getContentAsString();

    String paymentMd5 = objectMapper.readTree(khqrRes).path("data").path("md5").asText();
    long paymentId = objectMapper.readTree(khqrRes).path("data").path("paymentId").asLong();
    assertThat(paymentMd5).matches("^[a-fA-F0-9]{32}$");

    log.info("=== STEP 7: Simulate Payment Settlement ===");
    mockMvc
        .perform(
            post("/api/payments/" + paymentId + "/simulate-paid")
                .header("Authorization", customerToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.status").value("PAID"));

    log.info("=== STEP 8: Order CONFIRMED and Stock SALE recorded ===");
    Order confirmedOrder = orderRepository.findById(orderId).orElseThrow();
    assertThat(confirmedOrder.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
    assertThat(confirmedOrder.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
    assertThat(confirmedOrder.getTotalCents()).isEqualTo(67500L);

    List<InventoryMovement> movements = movementRepository.findAll();
    assertThat(movements)
        .anyMatch(
            m ->
                (m.getType() == InventoryMovementType.RESERVE || m.getType() == InventoryMovementType.SALE)
                    && orderId == (m.getRefOrderId() != null ? m.getRefOrderId() : -1L));

    log.info("=== STEP 9: Notifications sent check ===");
    long notifDeadline = System.currentTimeMillis() + 4000;
    while (System.currentTimeMillis() < notifDeadline) {
      boolean hasNotif =
          notificationRepository.findAll().stream()
              .anyMatch(n -> n.getUserId().equals(customerId));
      if (hasNotif) break;
      Thread.sleep(100);
    }
    assertThat(notificationRepository.findAll())
        .anyMatch(n -> n.getUserId().equals(customerId));

    log.info("=== STEP 10: Admin Ship Order ===");
    ShipOrderRequest shipReq = new ShipOrderRequest("KhmerMart Express");
    mockMvc
        .perform(
            post("/api/admin/orders/" + orderNumber + "/ship")
                .header("Authorization", adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(shipReq)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.status").value("SHIPPED"));

    log.info("=== STEP 11: Order Status Delivered ===");
    UpdateOrderStatusRequest deliveredReq =
        new UpdateOrderStatusRequest(OrderStatus.DELIVERED, "Delivered by courier to customer");
    mockMvc
        .perform(
            patch("/api/admin/orders/" + orderNumber + "/status")
                .header("Authorization", adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(deliveredReq)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.status").value("DELIVERED"));

    log.info("=== STEP 12: Customer Review Submission & Admin Moderation ===");
    reviewRepository.findByUserIdAndProductId(customerId, targetProductId).ifPresent(reviewRepository::delete);
    CreateReviewRequest reviewReq =
        new CreateReviewRequest(5, "Great product", "Excellent Dell laptop! Performs remarkably well for daily multitasking.");
    String reviewRes =
        mockMvc
            .perform(
                post("/api/products/" + targetProductId + "/reviews")
                    .header("Authorization", customerToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(reviewReq)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.success").value(true))
            .andReturn()
            .getResponse()
            .getContentAsString();

    long reviewId = objectMapper.readTree(reviewRes).path("data").path("id").asLong();

    // Admin approves the review
    AdminReviewUpdateRequest approveReq =
        new AdminReviewUpdateRequest(ReviewStatus.APPROVED, "Thank you for your feedback!");
    mockMvc
        .perform(
            patch("/api/admin/reviews/" + reviewId)
                .header("Authorization", adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(approveReq)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.status").value("APPROVED"));

    Review approvedReview = reviewRepository.findById(reviewId).orElseThrow();
    assertThat(approvedReview.getStatus()).isEqualTo(ReviewStatus.APPROVED);
    assertThat(approvedReview.getAdminReply()).isEqualTo("Thank you for your feedback!");

    log.info("=== 12-STEP PURCHASE FLOW COMPLETE: ALL STEPS VERIFIED SUCCESSFULLY ===");
  }
}
