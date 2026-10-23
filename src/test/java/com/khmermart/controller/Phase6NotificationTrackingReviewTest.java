package com.khmermart.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.khmermart.AbstractIntegrationTest;
import com.khmermart.event.InventoryLowEvent;
import com.khmermart.event.listener.NotificationEventListener;
import com.khmermart.model.entity.AppUser;
import com.khmermart.model.entity.Notification;
import com.khmermart.model.entity.Order;
import com.khmermart.model.entity.OrderItem;
import com.khmermart.model.entity.Payment;
import com.khmermart.model.entity.Product;
import com.khmermart.model.entity.PushSubscription;
import com.khmermart.model.enumerate.NotificationChannel;
import com.khmermart.model.enumerate.OrderStatus;
import com.khmermart.model.enumerate.PaymentMethod;
import com.khmermart.model.enumerate.PaymentStatus;
import com.khmermart.model.enumerate.ReviewStatus;
import com.khmermart.model.enumerate.Roles;
import com.khmermart.model.enumerate.ShipmentStatus;
import com.khmermart.model.request.AddTrackingEventRequest;
import com.khmermart.model.request.AdminReviewUpdateRequest;
import com.khmermart.model.request.CreateReviewRequest;
import com.khmermart.model.request.PushKeysRequest;
import com.khmermart.model.request.PushSubscriptionRequest;
import com.khmermart.model.request.UpdateReviewRequest;
import com.khmermart.repository.AppUserRepository;
import com.khmermart.repository.NotificationRepository;
import com.khmermart.repository.OrderItemRepository;
import com.khmermart.repository.OrderRepository;
import com.khmermart.repository.PaymentRepository;
import com.khmermart.repository.ProductRepository;
import com.khmermart.repository.PushSubscriptionRepository;
import com.khmermart.repository.ReviewRepository;
import com.khmermart.repository.ShipmentRepository;
import com.khmermart.security.JwtService;
import com.khmermart.service.PushSender;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
class Phase6NotificationTrackingReviewTest extends AbstractIntegrationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private JwtService jwtService;
  @Autowired private OrderRepository orderRepository;
  @Autowired private OrderItemRepository orderItemRepository;
  @Autowired private PaymentRepository paymentRepository;
  @Autowired private ProductRepository productRepository;
  @Autowired private ShipmentRepository shipmentRepository;
  @Autowired private ReviewRepository reviewRepository;
  @Autowired private NotificationRepository notificationRepository;
  @Autowired private PushSubscriptionRepository pushSubscriptionRepository;
  @Autowired private AppUserRepository appUserRepository;
  @Autowired private PushSender pushSender;
  @Autowired private NotificationEventListener notificationEventListener;

  private String customerToken;
  private String adminToken;
  private AppUser customerUser;
  private AppUser adminUser;
  private Product testProduct;

  @BeforeEach
  void setUp() {
    customerUser =
        appUserRepository
            .findByEmail("customer1@khmermart.local")
            .orElseGet(
                () ->
                    appUserRepository.save(
                        AppUser.builder()
                            .email("customer1@khmermart.local")
                            .passwordHash("$2a$10$dummy")
                            .fullName("Dara Sok")
                            .phone("+85512345678")
                            .role(Roles.CUSTOMER)
                            .emailVerifiedAt(Instant.now())
                            .build()));

    adminUser =
        appUserRepository
            .findByEmail("admin@khmermart.local")
            .orElseGet(
                () ->
                    appUserRepository.save(
                        AppUser.builder()
                            .email("admin@khmermart.local")
                            .passwordHash("$2a$10$dummy")
                            .fullName("Admin System")
                            .phone("+85512999999")
                            .role(Roles.ADMIN)
                            .emailVerifiedAt(Instant.now())
                            .build()));

    customerToken =
        "Bearer "
            + jwtService.generateAccessToken(
                customerUser.getId(), customerUser.getEmail(), Roles.CUSTOMER);
    adminToken =
        "Bearer "
            + jwtService.generateAccessToken(adminUser.getId(), adminUser.getEmail(), Roles.ADMIN);

    testProduct =
        productRepository.save(
            Product.builder()
                .sku(
                    "TEST-SKU-"
                        + System.currentTimeMillis()
                        + "-"
                        + UUID.randomUUID().toString().substring(0, 4))
                .name("Test Phase 6 Product")
                .slug("test-phase-6-product-" + UUID.randomUUID())
                .priceCents(5000L)
                .build());
  }

  @Test
  @DisplayName(
      "1 & 6. Tracking lifecycle: ship -> IN_TRANSIT -> OUT_FOR_DELIVERY -> DELIVERED, illegal jump, and COD auto-paid")
  void testTrackingLifecycleAndCodDelivery() throws Exception {
    // 1. Create a COD order in CONFIRMED status
    Order order =
        Order.builder()
            .orderNumber("KM-TEST-" + System.currentTimeMillis())
            .userId(customerUser.getId())
            .status(OrderStatus.CONFIRMED)
            .paymentStatus(PaymentStatus.PENDING)
            .paymentMethod(PaymentMethod.COD)
            .shippingMethodCode("STANDARD")
            .shippingAddress(
                "{\"recipientName\":\"Dara Sok\",\"phone\":\"+85512345678\",\"street\":\"St 2004\",\"provinceCode\":\"12\",\"communeCode\":\"120101\",\"districtCode\":\"1201\"}")
            .subtotalCents(5000L)
            .shippingCents(150L)
            .totalCents(5150L)
            .placedAt(Instant.now())
            .build();
    order = orderRepository.save(order);

    Payment payment =
        Payment.builder()
            .orderId(order.getId())
            .method(PaymentMethod.COD)
            .status(PaymentStatus.PENDING)
            .amountCents(5150L)
            .currency("USD")
            .createdAt(Instant.now())
            .build();
    paymentRepository.save(payment);

    // 2. Admin ships the order: POST /api/admin/orders/{orderNumber}/ship
    mockMvc
        .perform(
            post("/api/admin/orders/" + order.getOrderNumber() + "/ship")
                .header("Authorization", adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"carrier\":\"KhmerMart Express\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.status").value("SHIPPED"))
        .andExpect(jsonPath("$.data.carrier").value("KhmerMart Express"))
        .andExpect(
            jsonPath("$.data.trackingNumber").value(org.hamcrest.Matchers.startsWith("KMTRK")));

    Order shippedOrder = orderRepository.findById(order.getId()).orElseThrow();
    String trackingNum = shippedOrder.getTrackingNumber();

    // 3. Attempt illegal transition: DISPATCHED -> DELIVERED directly without IN_TRANSIT /
    // OUT_FOR_DELIVERY -> 422
    mockMvc
        .perform(
            post("/api/admin/orders/" + order.getOrderNumber() + "/tracking-events")
                .header("Authorization", adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new AddTrackingEventRequest(
                            ShipmentStatus.DELIVERED, "Phnom Penh", "Premature delivery"))))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.error.code").value("INVALID_STATUS_TRANSITION"));

    // 4. Proper transition: DISPATCHED -> IN_TRANSIT
    mockMvc
        .perform(
            post("/api/admin/orders/" + order.getOrderNumber() + "/tracking-events")
                .header("Authorization", adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new AddTrackingEventRequest(
                            ShipmentStatus.IN_TRANSIT,
                            "Phnom Penh Central Sorting",
                            "Departed facility"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("IN_TRANSIT"));

    // 5. IN_TRANSIT -> OUT_FOR_DELIVERY
    mockMvc
        .perform(
            post("/api/admin/orders/" + order.getOrderNumber() + "/tracking-events")
                .header("Authorization", adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new AddTrackingEventRequest(
                            ShipmentStatus.OUT_FOR_DELIVERY,
                            "Tuol Kouk Hub",
                            "Out with driver Sokly"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("OUT_FOR_DELIVERY"));

    Order outOrder = orderRepository.findById(order.getId()).orElseThrow();
    assertThat(outOrder.getStatus()).isEqualTo(OrderStatus.OUT_FOR_DELIVERY);

    // 6. OUT_FOR_DELIVERY -> DELIVERED (and COD payment auto-marked PAID)
    mockMvc
        .perform(
            post("/api/admin/orders/" + order.getOrderNumber() + "/tracking-events")
                .header("Authorization", adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new AddTrackingEventRequest(
                            ShipmentStatus.DELIVERED, "Customer Address", "Delivered and paid"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("DELIVERED"));

    Order delOrder = orderRepository.findById(order.getId()).orElseThrow();
    assertThat(delOrder.getStatus()).isEqualTo(OrderStatus.DELIVERED);
    assertThat(delOrder.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);

    Payment updatedPayment =
        paymentRepository.findFirstByOrderIdOrderByIdDesc(order.getId()).orElseThrow();
    assertThat(updatedPayment.getStatus()).isEqualTo(PaymentStatus.PAID);
    assertThat(updatedPayment.getPaidAt()).isNotNull();

    // 7. Public tracking endpoint: GET /api/track/{trackingNumber} (no auth needed)
    mockMvc
        .perform(get("/api/track/" + trackingNum))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.trackingNumber").value(trackingNum))
        .andExpect(jsonPath("$.data.status").value("DELIVERED"))
        .andExpect(jsonPath("$.data.carrier").value("KhmerMart Express"))
        .andExpect(jsonPath("$.data.events.length()").value(4));
  }

  @Test
  @DisplayName("3. In-app notifications: list, unread-count, mark single read, mark all read")
  void testNotificationEndpoints() throws Exception {
    // Insert 2 unread notifications for customer
    Notification n1 =
        Notification.builder()
            .userId(customerUser.getId())
            .channel(NotificationChannel.IN_APP)
            .type("ORDER_PLACED")
            .title("Order Confirmed")
            .body("Your order has been placed")
            .status("DELIVERED")
            .createdAt(Instant.now().minusSeconds(100))
            .build();
    n1 = notificationRepository.save(n1);

    Notification n2 =
        Notification.builder()
            .userId(customerUser.getId())
            .channel(NotificationChannel.IN_APP)
            .type("ORDER_SHIPPED")
            .title("Order Shipped")
            .body("Your order is on the way")
            .status("DELIVERED")
            .createdAt(Instant.now().minusSeconds(50))
            .build();
    n2 = notificationRepository.save(n2);

    // 1. GET /api/notifications
    mockMvc
        .perform(get("/api/notifications").header("Authorization", customerToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(
            jsonPath("$.data.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(2)));

    // 2. GET /api/notifications/unread-count
    mockMvc
        .perform(get("/api/notifications/unread-count").header("Authorization", customerToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(
            jsonPath("$.data.unreadCount").value(org.hamcrest.Matchers.greaterThanOrEqualTo(2)));

    // 3. PATCH /api/notifications/{id}/read
    mockMvc
        .perform(
            patch("/api/notifications/" + n1.getId() + "/read")
                .header("Authorization", customerToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.readAt").isNotEmpty());

    // 4. PATCH /api/notifications/read-all
    mockMvc
        .perform(patch("/api/notifications/read-all").header("Authorization", customerToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data").isEmpty());

    // Verify unread count is now 0
    mockMvc
        .perform(get("/api/notifications/unread-count").header("Authorization", customerToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.unreadCount").value(0));
  }

  @Test
  @DisplayName("4. Web push: subscribe, get VAPID key, test 404/410 eviction")
  void testWebPushAndEviction() throws Exception {
    // 1. GET /api/push/vapid-key
    mockMvc
        .perform(get("/api/push/vapid-key"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.publicKey").isNotEmpty());

    // 2. POST /api/push/subscribe
    PushSubscriptionRequest subReq =
        new PushSubscriptionRequest(
            "https://updates.push.services.mozilla.com/wpush/v2/test-subscriber-"
                + UUID.randomUUID(),
            new PushKeysRequest("BFh4k6T_p256dh_key_data", "auth_secret_key_1234"));

    mockMvc
        .perform(
            post("/api/push/subscribe")
                .header("Authorization", customerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(subReq)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true));

    assertThat(pushSubscriptionRepository.findByEndpoint(subReq.endpoint())).isPresent();

    // 3. Test invalid 404/410 subscription eviction:
    String expiredEndpoint =
        "https://fcm.googleapis.com/fcm/send/test-410-expired-" + UUID.randomUUID();
    PushSubscription expiredSub =
        PushSubscription.builder()
            .userId(customerUser.getId())
            .endpoint(expiredEndpoint)
            .p256dh("p256dh_expired")
            .auth("auth_expired")
            .build();
    expiredSub = pushSubscriptionRepository.save(expiredSub);

    assertThat(pushSubscriptionRepository.findByEndpoint(expiredEndpoint)).isPresent();

    // Trigger push delivery to this subscription -> detects 410 and evicts from DB
    pushSender.sendPushToSubscription(expiredSub, "Test Title", "Test Body", "/track");

    assertThat(pushSubscriptionRepository.findByEndpoint(expiredEndpoint)).isEmpty();
  }

  @Test
  @DisplayName(
      "7 & 8. Review lifecycle: unverified 403, verified 201, duplicate 409, admin approve recomputes rating & reply, items.canReview check")
  void testReviewLifecycleAndPermissions() throws Exception {
    // 1. Unverified purchase attempt -> 403 FORBIDDEN
    CreateReviewRequest createReq =
        new CreateReviewRequest(5, "Amazing Laptop!", "Blazing fast performance.");
    mockMvc
        .perform(
            post("/api/products/" + testProduct.getId() + "/reviews")
                .header("Authorization", customerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createReq)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.error.message").value("Only verified purchasers can review"));

    // 2. Create a DELIVERED order for this customer with testProduct
    Order order =
        Order.builder()
            .orderNumber("KM-DELIV-" + System.currentTimeMillis())
            .userId(customerUser.getId())
            .status(OrderStatus.DELIVERED)
            .paymentStatus(PaymentStatus.PAID)
            .paymentMethod(PaymentMethod.KHQR)
            .shippingMethodCode("STANDARD")
            .shippingAddress(
                "{\"recipientName\":\"Dara Sok\",\"phone\":\"+85512345678\",\"street\":\"St 2004\",\"provinceCode\":\"12\",\"communeCode\":\"120101\",\"districtCode\":\"1201\"}")
            .subtotalCents(testProduct.getPriceCents())
            .shippingCents(0L)
            .totalCents(testProduct.getPriceCents())
            .placedAt(Instant.now())
            .deliveredAt(Instant.now())
            .build();
    order = orderRepository.save(order);

    OrderItem item =
        OrderItem.builder()
            .orderId(order.getId())
            .productId(testProduct.getId())
            .productNameSnapshot(testProduct.getName())
            .unitPriceCents(testProduct.getPriceCents())
            .quantity(1)
            .lineTotalCents(testProduct.getPriceCents())
            .skuSnapshot(testProduct.getSku())
            .build();
    orderItemRepository.save(item);

    // 3. Verify GET /api/orders/{orderNumber} shows items[0].canReview: true
    mockMvc
        .perform(
            get("/api/orders/" + order.getOrderNumber()).header("Authorization", customerToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.canReview").value(true))
        .andExpect(jsonPath("$.data.items[0].canReview").value(true));

    // 4. Verified purchase review submission -> 201 CREATED
    String reviewResponse =
        mockMvc
            .perform(
                post("/api/products/" + testProduct.getId() + "/reviews")
                    .header("Authorization", customerToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(createReq)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.rating").value(5))
            .andExpect(jsonPath("$.data.status").value("PENDING"))
            .andReturn()
            .getResponse()
            .getContentAsString();

    Long reviewId = objectMapper.readTree(reviewResponse).path("data").path("id").asLong();

    // 5. GET /api/orders/{orderNumber} now shows items[0].canReview: false (already reviewed)
    mockMvc
        .perform(
            get("/api/orders/" + order.getOrderNumber()).header("Authorization", customerToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.canReview").value(false))
        .andExpect(jsonPath("$.data.items[0].canReview").value(false));

    // 6. Duplicate review submission by same user -> 409 CONFLICT
    mockMvc
        .perform(
            post("/api/products/" + testProduct.getId() + "/reviews")
                .header("Authorization", customerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createReq)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.error.message").value("Use PATCH /api/reviews/{id} to edit"));

    // 7. Customer edits their review -> 200 OK
    UpdateReviewRequest updateReq = new UpdateReviewRequest(4, "Updated title", "Still very good.");
    mockMvc
        .perform(
            patch("/api/reviews/" + reviewId)
                .header("Authorization", customerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateReq)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.rating").value(4));

    // 8. Admin approves review + replies -> rating recomputed and reply notification sent
    AdminReviewUpdateRequest adminReq =
        new AdminReviewUpdateRequest(
            ReviewStatus.APPROVED, "Thank you for your valuable feedback!");
    mockMvc
        .perform(
            patch("/api/admin/reviews/" + reviewId)
                .header("Authorization", adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(adminReq)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("APPROVED"))
        .andExpect(jsonPath("$.data.adminReply").value("Thank you for your valuable feedback!"));

    // 9. Public approved reviews list: GET /api/products/{id}/reviews
    mockMvc
        .perform(get("/api/products/" + testProduct.getId() + "/reviews"))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath("$.data.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)));
  }

  @Test
  @DisplayName("9. Low stock alert notification generated for admin when stock drops <= threshold")
  void testLowStockNotification() throws Exception {
    long initialCount =
        notificationRepository.findAll().stream()
            .filter(n -> "INVENTORY_LOW".equals(n.getType()))
            .count();

    notificationEventListener.handleInventoryLow(
        new InventoryLowEvent(
            testProduct.getId(), testProduct.getName(), testProduct.getSku(), 2, 5));

    Thread.sleep(300);

    long newCount =
        notificationRepository.findAll().stream()
            .filter(n -> "INVENTORY_LOW".equals(n.getType()))
            .count();

    assertThat(newCount).isGreaterThan(initialCount);
  }

  @Test
  @DisplayName("10. Dev email preview: renders HTML email correctly")
  void testDevEmailPreview() throws Exception {
    mockMvc
        .perform(get("/api/dev/email-preview/order-placed"))
        .andExpect(status().isOk())
        .andExpect(
            org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                .contentTypeCompatibleWith(MediaType.TEXT_HTML))
        .andExpect(
            org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                .string(org.hamcrest.Matchers.containsString("KhmerMart • ផ្សារខ្មែរ")));

    mockMvc
        .perform(get("/api/dev/email-preview/payment-paid"))
        .andExpect(status().isOk())
        .andExpect(
            org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                .string(org.hamcrest.Matchers.containsString("Payment Received")));

    mockMvc
        .perform(get("/api/dev/email-preview/order-shipped"))
        .andExpect(status().isOk())
        .andExpect(
            org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                .string(org.hamcrest.Matchers.containsString("Order Shipped")));
  }
}
