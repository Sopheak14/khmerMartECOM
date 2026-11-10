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
import com.khmermart.repository.InventoryMovementRepository;
import com.khmermart.repository.NotificationRepository;
import com.khmermart.repository.OrderRepository;
import com.khmermart.repository.PaymentRepository;
import com.khmermart.repository.ReviewRepository;
import com.khmermart.seed.SeedCatalog;
import com.khmermart.service.ReviewService;
import java.util.List;
import java.util.Map;
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
  @Autowired private ReviewService reviewService;

  @Test
  @DisplayName("Full 12-step lesson scenario as ONE comprehensive integration test")
  void testFullTwelveStepPurchaseFlow() throws Exception {
    log.info("=== STEP 1: Authentication (throwaway Customer & seeded Admin) ===");
    // A brand-new customer per run: the seeded customers' carts, reviews and coupon redemptions are
    // shared with other test classes, which made this flow depend on test class order.
    String customerEmail = "e2e+" + UUID.randomUUID() + "@khmermart.local";
    String registerRes =
        mockMvc
            .perform(
                post("/api/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsString(
                            Map.of(
                                "email",
                                customerEmail,
                                "password",
                                "E2eFlow@12345",
                                "fullName",
                                "Sopheap Customer",
                                "phone",
                                "+85512345678"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andReturn()
            .getResponse()
            .getContentAsString();
    markVerified(customerEmail);
    String customerLoginRes =
        mockMvc
            .perform(
                post("/api/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsString(
                            Map.of("email", customerEmail, "password", "E2eFlow@12345"))))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String customerToken =
        "Bearer " + objectMapper.readTree(customerLoginRes).path("data").path("accessToken").asText();
    long customerId = userId(customerEmail);

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

    log.info("=== STEP 2: Search 'Silk' with filters (brand=Takeo Weavers) ===");
    String searchRes =
        mockMvc
            .perform(get("/api/products?q=Silk&brand=Takeo Weavers"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(
                jsonPath("$.data.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)))
            .andReturn()
            .getResponse()
            .getContentAsString();

    // The hero product (Takeo Hol Silk Sampot) is the target; buy its Ruby Red Standard variant
    SeedCatalog.Product hero = SeedCatalog.product(SeedCatalog.HERO_ID);
    long targetProductId = hero.id();
    String targetSlug = hero.slug();
    long subtotal = SeedCatalog.HERO_STANDARD_CENTS;
    long discount = SeedCatalog.DEMO_ORDER_FLASH_DISCOUNT_CENTS + SeedCatalog.SAVE5_CENTS;
    long total = SeedCatalog.DEMO_ORDER_TOTAL_CENTS;

    log.info("=== STEP 3: Product detail by slug ({}) ===", targetSlug);
    String detailRes =
        mockMvc
            .perform(get("/api/products/" + targetSlug))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.id").value(targetProductId))
            .andExpect(jsonPath("$.data.name").value(hero.name()))
            .andExpect(jsonPath("$.data.priceCents").value(subtotal))
            .andReturn()
            .getResponse()
            .getContentAsString();

    JsonNode detailJson = objectMapper.readTree(detailRes).path("data");
    assertThat(detailJson.path("variants").size()).isGreaterThanOrEqualTo(1);
    assertThat(detailJson.path("variants").size()).isEqualTo(4);
    long variantId = detailJson.path("variants").get(0).path("id").asLong();
    long variantPrice = detailJson.path("variants").get(0).path("priceCents").asLong();
    assertThat(variantId).isEqualTo(SeedCatalog.HERO_RED_STANDARD);
    assertThat(variantPrice).isEqualTo(SeedCatalog.HERO_STANDARD_CENTS);

    log.info(
        "=== STEP 4: Add to Cart (productId: {}, variantId: {}) ===", targetProductId, variantId);
    mockMvc
        .perform(
            post("/api/cart/items")
                .header("Authorization", customerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new CartItemRequest(targetProductId, variantId, 1))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.subtotalCents").value(subtotal));

    log.info("=== STEP 5: Apply Coupon SAVE5 ===");
    mockMvc
        .perform(
            post("/api/cart/coupon")
                .header("Authorization", customerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new ApplyCouponRequest(SeedCatalog.COUPON_SAVE5))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.subtotalCents").value(subtotal))
        .andExpect(jsonPath("$.data.coupon.code").value(SeedCatalog.COUPON_SAVE5))
        .andExpect(jsonPath("$.data.coupon.value").value(SeedCatalog.SAVE5_CENTS))
        // flash sale (14900 -> 12900) + SAVE5 coupon
        .andExpect(jsonPath("$.data.discountCents").value(discount))
        .andExpect(jsonPath("$.data.totalCents").value(total));

    log.info("=== STEP 6: Checkout with KHQR ===");
    Address address =
        addressRepository.findByUserIdOrderByIdAsc(customerId).stream()
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
            .andExpect(jsonPath("$.data.order.subtotalCents").value(subtotal))
            .andExpect(jsonPath("$.data.order.discountCents").value(discount))
            .andExpect(
                jsonPath("$.data.order.shippingCents")
                    .value(0)) // STANDARD shipping is free over the 5000-cent threshold
            .andExpect(jsonPath("$.data.order.totalCents").value(total)) // subtotal - discount
            .andExpect(jsonPath("$.data.next").isNotEmpty())
            .andReturn()
            .getResponse()
            .getContentAsString();

    String orderNumber =
        objectMapper.readTree(checkoutRes).path("data").path("order").path("orderNumber").asText();
    long orderId =
        objectMapper.readTree(checkoutRes).path("data").path("order").path("id").asLong();

    // Verify 6-digit order number format: KM-YYYYMMDD-NNNNNN
    assertThat(orderNumber).matches("^KM-\\d{8}-\\d{6}$");

    // Generate KHQR payment details for the order
    String khqrRes =
        mockMvc
            .perform(
                post("/api/payments/khqr/" + orderNumber).header("Authorization", customerToken))
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
    // 12400 USD cents -> 508,400 riel at the seeded 4100 rate
    assertThat(paymentRepository.findById(paymentId).orElseThrow().getAmountKhr())
        .isEqualTo(SeedCatalog.DEMO_ORDER_TOTAL_KHR);

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
    assertThat(confirmedOrder.getTotalCents()).isEqualTo(total);

    List<InventoryMovement> movements = movementRepository.findAll();
    assertThat(movements)
        .anyMatch(
            m ->
                (m.getType() == InventoryMovementType.RESERVE
                        || m.getType() == InventoryMovementType.SALE)
                    && orderId == (m.getRefOrderId() != null ? m.getRefOrderId() : -1L));

    log.info("=== STEP 9: Notifications sent check ===");
    long notifDeadline = System.currentTimeMillis() + 4000;
    while (System.currentTimeMillis() < notifDeadline) {
      boolean hasNotif =
          notificationRepository.findAll().stream().anyMatch(n -> n.getUserId().equals(customerId));
      if (hasNotif) break;
      Thread.sleep(100);
    }
    assertThat(notificationRepository.findAll()).anyMatch(n -> n.getUserId().equals(customerId));

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
    CreateReviewRequest reviewReq =
        new CreateReviewRequest(
            5,
            "Great product",
            "Beautiful hand-woven silk sampot, the Ruby Red colour is rich and the weave is even.");
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

    // The hero product is shared with other test classes: put its seeded rating back
    reviewRepository.deleteById(reviewId);
    reviewService.recomputeProductRating(targetProductId);

    log.info("=== 12-STEP PURCHASE FLOW COMPLETE: ALL STEPS VERIFIED SUCCESSFULLY ===");
  }
}
