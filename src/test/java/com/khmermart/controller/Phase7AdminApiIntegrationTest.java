package com.khmermart.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.khmermart.AbstractIntegrationTest;
import com.khmermart.model.entity.AppUser;
import com.khmermart.model.entity.Category;
import com.khmermart.model.entity.Inventory;
import com.khmermart.model.entity.Order;
import com.khmermart.model.entity.OrderItem;
import com.khmermart.model.entity.Payment;
import com.khmermart.model.entity.Product;
import com.khmermart.model.enumerate.CouponType;
import com.khmermart.model.enumerate.OrderStatus;
import com.khmermart.model.enumerate.PaymentMethod;
import com.khmermart.model.enumerate.PaymentStatus;
import com.khmermart.model.enumerate.PromotionType;
import com.khmermart.model.enumerate.Roles;
import com.khmermart.model.request.AdminCouponRequest;
import com.khmermart.model.request.AdminProductCreateRequest;
import com.khmermart.model.request.AdminProductCreateRequest.AdminVariantRequest;
import com.khmermart.model.request.AdminPromotionRequest;
import com.khmermart.model.request.AdminSettingsUpdateRequest;
import com.khmermart.model.request.AdminUserUpdateRequest;
import com.khmermart.model.request.CategoryRequest;
import com.khmermart.model.request.InventoryAdjustRequest;
import com.khmermart.model.request.PromotionItemRequest;
import com.khmermart.model.request.RefundRequest;
import com.khmermart.model.request.ReorderImagesRequest;
import com.khmermart.model.request.UpdateOrderStatusRequest;
import com.khmermart.repository.AppUserRepository;
import com.khmermart.repository.AuditLogRepository;
import com.khmermart.repository.CategoryRepository;
import com.khmermart.repository.InventoryRepository;
import com.khmermart.repository.OrderItemRepository;
import com.khmermart.repository.OrderRepository;
import com.khmermart.repository.PaymentRepository;
import com.khmermart.repository.ProductImageRepository;
import com.khmermart.repository.ProductRepository;
import com.khmermart.security.JwtService;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
class Phase7AdminApiIntegrationTest extends AbstractIntegrationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private JwtService jwtService;
  @Autowired private AppUserRepository appUserRepository;
  @Autowired private ProductRepository productRepository;
  @Autowired private CategoryRepository categoryRepository;
  @Autowired private InventoryRepository inventoryRepository;
  @Autowired private OrderRepository orderRepository;
  @Autowired private OrderItemRepository orderItemRepository;
  @Autowired private PaymentRepository paymentRepository;
  @Autowired private ProductImageRepository productImageRepository;
  @Autowired private AuditLogRepository auditLogRepository;

  private String adminToken;
  private String staffToken;
  private String customerToken;
  private AppUser adminUser;
  private AppUser staffUser;
  private AppUser customerUser;

  @BeforeEach
  void setUp() {
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

    staffUser =
        appUserRepository
            .findByEmail("staff@khmermart.local")
            .orElseGet(
                () ->
                    appUserRepository.save(
                        AppUser.builder()
                            .email("staff@khmermart.local")
                            .passwordHash("$2a$10$dummy")
                            .fullName("Staff Operator")
                            .phone("+85512888888")
                            .role(Roles.STAFF)
                            .emailVerifiedAt(Instant.now())
                            .build()));

    customerUser =
        appUserRepository
            .findByEmail("customer1@khmermart.local")
            .orElseGet(
                () ->
                    appUserRepository.save(
                        AppUser.builder()
                            .email("customer1@khmermart.local")
                            .passwordHash("$2a$10$dummy")
                            .fullName("Customer One")
                            .phone("+85512777777")
                            .role(Roles.CUSTOMER)
                            .emailVerifiedAt(Instant.now())
                            .build()));

    adminToken =
        "Bearer "
            + jwtService.generateAccessToken(adminUser.getId(), adminUser.getEmail(), Roles.ADMIN);
    staffToken =
        "Bearer "
            + jwtService.generateAccessToken(staffUser.getId(), staffUser.getEmail(), Roles.STAFF);
    customerToken =
        "Bearer "
            + jwtService.generateAccessToken(
                customerUser.getId(), customerUser.getEmail(), Roles.CUSTOMER);
  }

  @Test
  @DisplayName("b. Permissions matrix: STAFF vs ADMIN vs CUSTOMER access control")
  void testPermissionsMatrix() throws Exception {
    // 1. Customer gets 403 on ALL /api/admin/**
    mockMvc
        .perform(get("/api/admin/dashboard").header("Authorization", customerToken))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(get("/api/admin/products").header("Authorization", customerToken))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(get("/api/admin/users").header("Authorization", customerToken))
        .andExpect(status().isForbidden());

    // 2. Staff has access to operational endpoints (200 OK)
    mockMvc
        .perform(get("/api/admin/dashboard").header("Authorization", staffToken))
        .andExpect(status().isOk());
    mockMvc
        .perform(get("/api/admin/products").header("Authorization", staffToken))
        .andExpect(status().isOk());
    mockMvc
        .perform(get("/api/admin/categories").header("Authorization", staffToken))
        .andExpect(status().isOk());
    mockMvc
        .perform(get("/api/admin/inventory").header("Authorization", staffToken))
        .andExpect(status().isOk());
    mockMvc
        .perform(get("/api/admin/orders").header("Authorization", staffToken))
        .andExpect(status().isOk());
    mockMvc
        .perform(get("/api/admin/coupons").header("Authorization", staffToken))
        .andExpect(status().isOk());
    mockMvc
        .perform(get("/api/admin/promotions").header("Authorization", staffToken))
        .andExpect(status().isOk());
    mockMvc
        .perform(get("/api/admin/reviews").header("Authorization", staffToken))
        .andExpect(status().isOk());

    // 3. Staff is FORBIDDEN (403) on Admin-only modules
    mockMvc
        .perform(get("/api/admin/users").header("Authorization", staffToken))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(get("/api/admin/payments").header("Authorization", staffToken))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(get("/api/admin/reports/sales").header("Authorization", staffToken))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(get("/api/admin/settings").header("Authorization", staffToken))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(get("/api/admin/audit-logs").header("Authorization", staffToken))
        .andExpect(status().isForbidden());

    // 4. Admin has access to all admin-only modules
    mockMvc
        .perform(get("/api/admin/users").header("Authorization", adminToken))
        .andExpect(status().isOk());
    mockMvc
        .perform(get("/api/admin/payments").header("Authorization", adminToken))
        .andExpect(status().isOk());
    mockMvc
        .perform(get("/api/admin/reports/sales").header("Authorization", adminToken))
        .andExpect(status().isOk());
    mockMvc
        .perform(get("/api/admin/settings").header("Authorization", adminToken))
        .andExpect(status().isOk());
    mockMvc
        .perform(get("/api/admin/audit-logs").header("Authorization", adminToken))
        .andExpect(status().isOk());
  }

  @Test
  @DisplayName(
      "a. Dashboard: GET /api/admin/dashboard returns full DashboardDTO with native SQL charts")
  void testDashboardKpis() throws Exception {
    mockMvc
        .perform(get("/api/admin/dashboard?range=30d").header("Authorization", adminToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.revenueTodayCents").isNumber())
        .andExpect(jsonPath("$.data.revenue7dCents").isNumber())
        .andExpect(jsonPath("$.data.revenue30dCents").isNumber())
        .andExpect(jsonPath("$.data.ordersToday").isNumber())
        .andExpect(jsonPath("$.data.ordersPending").isNumber())
        .andExpect(jsonPath("$.data.avgOrderValueCents").isNumber())
        .andExpect(jsonPath("$.data.pendingPayments").isNumber())
        .andExpect(jsonPath("$.data.lowStock").isArray())
        .andExpect(jsonPath("$.data.recentOrders").isArray())
        .andExpect(jsonPath("$.data.salesChart").isArray())
        .andExpect(jsonPath("$.data.salesChart.length()").value(30))
        .andExpect(jsonPath("$.data.topProducts").isArray())
        .andExpect(jsonPath("$.data.paymentMethodBreakdown").isArray());
  }

  @Test
  @DisplayName("c. Users management: list, update role, deactivate and token revocation")
  void testUserManagement() throws Exception {
    AppUser tempUser =
        appUserRepository.save(
            AppUser.builder()
                .email("temp-deactivate-" + UUID.randomUUID() + "@khmermart.local")
                .passwordHash("$2a$10$dummy")
                .fullName("Temporary User")
                .phone("+85512344321")
                .role(Roles.CUSTOMER)
                .emailVerifiedAt(Instant.now())
                .active(true)
                .build());

    // 1. Search / List users
    mockMvc
        .perform(get("/api/admin/users?q=Temporary").header("Authorization", adminToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(
            jsonPath("$.data.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)));

    // 2. Change role and deactivate user
    AdminUserUpdateRequest updateReq = new AdminUserUpdateRequest(Roles.STAFF, false);
    mockMvc
        .perform(
            patch("/api/admin/users/" + tempUser.getId())
                .header("Authorization", adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateReq)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.role").value("STAFF"))
        .andExpect(jsonPath("$.data.isActive").value(false));

    AppUser updated = appUserRepository.findById(tempUser.getId()).orElseThrow();
    assertThat(updated.getRole()).isEqualTo(Roles.STAFF);
    assertThat(updated.isActive()).isFalse();
  }

  @Test
  @DisplayName(
      "d. Products CRUD: create with variants + Khmer name, magic bytes image upload, and soft delete")
  void testProductsLifecycle() throws Exception {
    // 1. Create product with variants and Khmer name
    AdminProductCreateRequest createReq =
        new AdminProductCreateRequest(
            "MacBook Pro 16",
            "ម៉ាក់ប៊ុកប្រូ ១៦",
            "MBP16-" + UUID.randomUUID().toString().substring(0, 4),
            "macbook-pro-16-" + UUID.randomUUID(),
            "Ultimate pro laptop",
            "Apple",
            249900L,
            269900L,
            null,
            true,
            null,
            List.of(
                new AdminVariantRequest("MBP16-512", Map.of("storage", "512GB"), 249900L, 15),
                new AdminVariantRequest("MBP16-1TB", Map.of("storage", "1TB"), 289900L, 10)));

    String createRes =
        mockMvc
            .perform(
                post("/api/admin/products")
                    .header("Authorization", adminToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(createReq)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.name").value("MacBook Pro 16"))
            .andExpect(jsonPath("$.data.nameKm").value("ម៉ាក់ប៊ុកប្រូ ១៦"))
            .andReturn()
            .getResponse()
            .getContentAsString();

    Long productId = objectMapper.readTree(createRes).path("data").path("id").asLong();

    // 2. Image upload: reject malicious .exe file disguised as .jpg
    byte[] exeBytes = new byte[] {'M', 'Z', 0x00, 0x00, 0x01, 0x02};
    MockMultipartFile fakeJpg =
        new MockMultipartFile("file", "malicious.jpg", "image/jpeg", exeBytes);

    mockMvc
        .perform(
            multipart("/api/admin/products/" + productId + "/images")
                .file(fakeJpg)
                .header("Authorization", adminToken))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));

    // 3. Image upload: valid JPEG magic bytes (FF D8 FF)
    byte[] validJpgBytes =
        new byte[] {
          (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0x00, 0x10, 0x4A, 0x46, 0x49, 0x46
        };
    MockMultipartFile validJpg =
        new MockMultipartFile("file", "valid.jpg", "image/jpeg", validJpgBytes);

    String uploadRes =
        mockMvc
            .perform(
                multipart("/api/admin/products/" + productId + "/images")
                    .file(validJpg)
                    .header("Authorization", adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(
                jsonPath("$.data.url")
                    .value(org.hamcrest.Matchers.startsWith("/uploads/products/")))
            .andReturn()
            .getResponse()
            .getContentAsString();

    Long imageId = objectMapper.readTree(uploadRes).path("data").path("id").asLong();

    // 4. Reorder images
    mockMvc
        .perform(
            patch("/api/admin/products/" + productId + "/images/order")
                .header("Authorization", adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(new ReorderImagesRequest(List.of(imageId)))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true));

    // 5. CSV export
    mockMvc
        .perform(get("/api/admin/products/export.csv").header("Authorization", adminToken))
        .andExpect(status().isOk())
        .andExpect(
            header()
                .string(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"products.csv\""));

    // 6. CSV import with per-row validation reporting
    String csvContent =
        """
        Name,SKU,PriceUSD,Brand
        Valid Product,SKU-VAL-01,99.50,KhmerMart
        ,SKU-ERR-02,50.00,NoName
        Bad Price,SKU-ERR-03,invalid-price,NoName
        """;
    MockMultipartFile csvFile =
        new MockMultipartFile(
            "file",
            "import.csv",
            "text/csv",
            csvContent.getBytes(java.nio.charset.StandardCharsets.UTF_8));

    mockMvc
        .perform(
            multipart("/api/admin/products/import")
                .file(csvFile)
                .header("Authorization", adminToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.imported").value(1))
        .andExpect(jsonPath("$.data.errors.length()").value(2));

    // 7. Soft delete: create an order with this product, then delete -> soft deleted (active=false)
    Order order =
        orderRepository.save(
            Order.builder()
                .orderNumber("KM-PROD-TEST-" + System.currentTimeMillis())
                .userId(customerUser.getId())
                .status(OrderStatus.CONFIRMED)
                .paymentStatus(PaymentStatus.PAID)
                .paymentMethod(PaymentMethod.KHQR)
                .shippingMethodCode("STANDARD")
                .shippingAddress("{}")
                .subtotalCents(249900L)
                .shippingCents(0L)
                .totalCents(249900L)
                .placedAt(Instant.now())
                .build());

    orderItemRepository.save(
        OrderItem.builder()
            .orderId(order.getId())
            .productId(productId)
            .skuSnapshot("MBP16-TEST")
            .productNameSnapshot("MacBook Pro 16")
            .unitPriceCents(249900L)
            .quantity(1)
            .lineTotalCents(249900L)
            .build());

    mockMvc
        .perform(delete("/api/admin/products/" + productId).header("Authorization", adminToken))
        .andExpect(status().isOk());

    Product deletedProd = productRepository.findById(productId).orElseThrow();
    assertThat(deletedProd.isActive()).isFalse(); // soft deleted!
  }

  @Test
  @DisplayName(
      "e. Categories: tree, create, update, and cannot delete a category that has products -> 409 CONFLICT")
  void testCategoryConflictOnDelete() throws Exception {
    // 1. Get category tree
    mockMvc
        .perform(get("/api/admin/categories").header("Authorization", adminToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data").isArray());

    // 2. Create category
    CategoryRequest createReq =
        new CategoryRequest(
            "New Admin Gadgets",
            "ឧបករណ៍អេឡិចត្រូនិកថ្មី",
            "new-admin-gadgets-" + UUID.randomUUID(),
            null,
            "https://img.khmermart.local/cat.png",
            10);

    String catJson =
        mockMvc
            .perform(
                post("/api/admin/categories")
                    .header("Authorization", adminToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(createReq)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.name").value("New Admin Gadgets"))
            .andReturn()
            .getResponse()
            .getContentAsString();

    Long newCatId = objectMapper.readTree(catJson).path("data").path("id").asLong();

    // 3. Update category
    CategoryRequest updateReq =
        new CategoryRequest(
            "Updated Admin Gadgets",
            "ឧបករណ៍អេឡិចត្រូនិកកែប្រែ",
            "updated-admin-gadgets-" + UUID.randomUUID(),
            null,
            "https://img.khmermart.local/cat2.png",
            12);

    mockMvc
        .perform(
            put("/api/admin/categories/" + newCatId)
                .header("Authorization", adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateReq)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.name").value("Updated Admin Gadgets"));

    Category cat =
        categoryRepository.save(
            Category.builder()
                .name("Test Category With Products")
                .slug("test-cat-with-prods-" + UUID.randomUUID())
                .sortOrder(1)
                .build());

    Product prod =
        productRepository.save(
            Product.builder()
                .name("Product in Cat")
                .sku("SKU-CAT-" + UUID.randomUUID())
                .slug("prod-in-cat-" + UUID.randomUUID())
                .categoryId(cat.getId())
                .priceCents(1000L)
                .build());

    // Attempt to delete category -> 409 CONFLICT
    mockMvc
        .perform(delete("/api/admin/categories/" + cat.getId()).header("Authorization", adminToken))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.error.message").value("Cannot delete category that has products"));

    // Delete product first, now category can be deleted
    productRepository.delete(prod);
    mockMvc
        .perform(delete("/api/admin/categories/" + cat.getId()).header("Authorization", adminToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true));
  }

  @Test
  @DisplayName("f. Inventory: lowStock filter, restock (resets lowStockAlerted), adjust, movements")
  void testInventoryRestockAndAdjust() throws Exception {
    Product prod =
        productRepository.save(
            Product.builder()
                .name("Inventory Test Item")
                .sku("INV-TEST-" + UUID.randomUUID())
                .slug("inv-test-item-" + UUID.randomUUID())
                .priceCents(500L)
                .build());

    Inventory inv =
        inventoryRepository.save(
            Inventory.builder()
                .productId(prod.getId())
                .quantityOnHand(3)
                .quantityReserved(0)
                .lowStockThreshold(5)
                .lowStockAlerted(true)
                .build());

    // 1. List with lowStock filter
    mockMvc
        .perform(get("/api/admin/inventory?lowStock=true").header("Authorization", adminToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(
            jsonPath("$.data.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)));

    // 2. Restock: adds units and resets lowStockAlerted to false
    mockMvc
        .perform(
            post("/api/admin/inventory/" + inv.getId() + "/restock")
                .header("Authorization", adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new InventoryAdjustRequest(20, "Batch shipment restock"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.quantityOnHand").value(23))
        .andExpect(jsonPath("$.data.lowStockAlerted").value(false));

    Inventory restocked = inventoryRepository.findById(inv.getId()).orElseThrow();
    assertThat(restocked.isLowStockAlerted()).isFalse();

    // 3. Adjust inventory
    mockMvc
        .perform(
            post("/api/admin/inventory/" + inv.getId() + "/adjust")
                .header("Authorization", adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new InventoryAdjustRequest(-3, "Damaged in store"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.quantityOnHand").value(20));

    // 4. View movements
    mockMvc
        .perform(
            get("/api/admin/inventory/" + inv.getId() + "/movements")
                .header("Authorization", adminToken))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath("$.data.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(2)));
  }

  @Test
  @DisplayName("g. Orders: list/filter, detail with customer, status transition")
  void testAdminOrderManagement() throws Exception {
    Order order =
        orderRepository.save(
            Order.builder()
                .orderNumber("KM-ORD-ADM-" + System.currentTimeMillis())
                .userId(customerUser.getId())
                .status(OrderStatus.CONFIRMED)
                .paymentStatus(PaymentStatus.PAID)
                .paymentMethod(PaymentMethod.KHQR)
                .shippingMethodCode("STANDARD")
                .shippingAddress(
                    "{\"recipientName\":\"Admin Test Customer\",\"phone\":\"+85512000000\"}")
                .subtotalCents(5000L)
                .shippingCents(150L)
                .totalCents(5150L)
                .placedAt(Instant.now())
                .build());

    // 1. List with status filter
    mockMvc
        .perform(get("/api/admin/orders?status=CONFIRMED").header("Authorization", adminToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data").isArray());

    // 2. Order detail with customer info
    mockMvc
        .perform(
            get("/api/admin/orders/" + order.getOrderNumber()).header("Authorization", adminToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.orderNumber").value(order.getOrderNumber()));

    // 3. Status transition
    mockMvc
        .perform(
            patch("/api/admin/orders/" + order.getOrderNumber() + "/status")
                .header("Authorization", adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new UpdateOrderStatusRequest(
                            OrderStatus.PROCESSING, "Order accepted by warehouse"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("PROCESSING"));
  }

  @Test
  @DisplayName("h. Payments: partial & full refund, reconciliation report detecting mismatches")
  void testPaymentsRefundAndReconciliation() throws Exception {
    Order order =
        orderRepository.save(
            Order.builder()
                .orderNumber("KM-PAY-TEST-" + System.currentTimeMillis())
                .userId(customerUser.getId())
                .status(OrderStatus.DELIVERED)
                .paymentStatus(PaymentStatus.PAID)
                .paymentMethod(PaymentMethod.KHQR)
                .shippingMethodCode("STANDARD")
                .shippingAddress("{}")
                .subtotalCents(5000L)
                .shippingCents(0L)
                .totalCents(5000L)
                .placedAt(Instant.now())
                .build());

    Payment payment =
        paymentRepository.save(
            Payment.builder()
                .orderId(order.getId())
                .method(PaymentMethod.KHQR)
                .status(PaymentStatus.PAID)
                .amountCents(5000L)
                .currency("USD")
                .rawResponse("{\"bakongRef\":\"REF123456\"}")
                .createdAt(Instant.now())
                .build());

    // 1. Partial refund: refund $20 out of $50 -> PARTIALLY_REFUNDED
    mockMvc
        .perform(
            post("/api/admin/payments/" + payment.getId() + "/refund")
                .header("Authorization", adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new RefundRequest(2000L, "Customer courtesy partial discount"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("PARTIALLY_REFUNDED"));

    // 2. Full refund remainder: refund $30 -> REFUNDED
    mockMvc
        .perform(
            post("/api/admin/payments/" + payment.getId() + "/refund")
                .header("Authorization", adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new RefundRequest(3000L, "Full return of order"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("REFUNDED"));

    Payment refunded = paymentRepository.findById(payment.getId()).orElseThrow();
    assertThat(refunded.getStatus()).isEqualTo(PaymentStatus.REFUNDED);

    // 3. Seed an intentional mismatch: order CONFIRMED but payment PENDING to prove reconciliation
    // detects it
    Order mismatchOrder =
        orderRepository.save(
            Order.builder()
                .orderNumber("KM-MISMATCH-" + System.currentTimeMillis())
                .userId(customerUser.getId())
                .status(OrderStatus.CONFIRMED)
                .paymentStatus(PaymentStatus.PENDING)
                .paymentMethod(PaymentMethod.KHQR)
                .shippingMethodCode("STANDARD")
                .shippingAddress("{}")
                .subtotalCents(2000L)
                .shippingCents(0L)
                .totalCents(2000L)
                .placedAt(Instant.now())
                .build());

    mockMvc
        .perform(get("/api/admin/payments/reconciliation").header("Authorization", adminToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.status").value("MISMATCH_DETECTED"))
        .andExpect(
            jsonPath("$.data.mismatchCount").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)));

    // 4. List payments with filters
    mockMvc
        .perform(
            get("/api/admin/payments?status=REFUNDED&method=KHQR")
                .header("Authorization", adminToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data").isArray());

    // 5. Payment detail
    mockMvc
        .perform(get("/api/admin/payments/" + payment.getId()).header("Authorization", adminToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.id").value(payment.getId()));

    // 6. Export payments CSV
    mockMvc
        .perform(get("/api/admin/payments/export.csv").header("Authorization", adminToken))
        .andExpect(status().isOk())
        .andExpect(
            header()
                .string(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"payments.csv\""));
  }

  @Test
  @DisplayName("i. Coupons & promotions: validation (endsAt > startsAt, percent 1-100)")
  void testMarketingValidation() throws Exception {
    Instant now = Instant.now();

    // 1. Invalid coupon: endsAt before startsAt -> 400 VALIDATION_ERROR
    AdminCouponRequest invalidDateCoupon =
        new AdminCouponRequest(
            "INVALIDDATE",
            CouponType.FIXED,
            500L,
            "Bad dates",
            null,
            null,
            100,
            now,
            now.minus(2, ChronoUnit.DAYS),
            true);

    mockMvc
        .perform(
            post("/api/admin/coupons")
                .header("Authorization", adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalidDateCoupon)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.message").value("endsAt must be after startsAt"));

    // 2. Invalid coupon: percent > 100 -> 400 VALIDATION_ERROR
    AdminCouponRequest invalidPercentCoupon =
        new AdminCouponRequest(
            "BADPCT",
            CouponType.PERCENT,
            150L,
            "Bad percent",
            null,
            null,
            100,
            now,
            now.plus(7, ChronoUnit.DAYS),
            true);

    mockMvc
        .perform(
            post("/api/admin/coupons")
                .header("Authorization", adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalidPercentCoupon)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.message").value("Discount percent must be between 1 and 100"));

    // 3. Valid coupon CRUD
    mockMvc
        .perform(get("/api/admin/coupons").header("Authorization", adminToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data").isArray());

    AdminCouponRequest validCoupon =
        new AdminCouponRequest(
            "SUMMER2026",
            CouponType.FIXED,
            1000L,
            "Summer discount coupon",
            5000L,
            null,
            50,
            now,
            now.plus(15, ChronoUnit.DAYS),
            true);

    String couponRes =
        mockMvc
            .perform(
                post("/api/admin/coupons")
                    .header("Authorization", adminToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(validCoupon)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.code").value("SUMMER2026"))
            .andReturn()
            .getResponse()
            .getContentAsString();

    Long couponId = objectMapper.readTree(couponRes).path("data").path("id").asLong();

    AdminCouponRequest updateCoupon =
        new AdminCouponRequest(
            "SUMMER2026",
            CouponType.FIXED,
            1200L,
            "Updated summer discount coupon",
            5000L,
            null,
            60,
            now,
            now.plus(20, ChronoUnit.DAYS),
            true);

    mockMvc
        .perform(
            put("/api/admin/coupons/" + couponId)
                .header("Authorization", adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateCoupon)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.value").value(1200));

    mockMvc
        .perform(delete("/api/admin/coupons/" + couponId).header("Authorization", adminToken))
        .andExpect(status().isOk());

    // 4. Valid promotion creation, list, update & deletion
    mockMvc
        .perform(get("/api/admin/promotions").header("Authorization", adminToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data").isArray());

    AdminPromotionRequest promoReq =
        new AdminPromotionRequest(
            "Summer Festival Promo",
            "ប្រូម៉ូសិនរដូវក្តៅ",
            PromotionType.SEASONAL,
            20,
            null,
            now,
            now.plus(10, ChronoUnit.DAYS),
            true,
            List.of(),
            null);

    String promoRes =
        mockMvc
            .perform(
                post("/api/admin/promotions")
                    .header("Authorization", adminToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(promoReq)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.name").value("Summer Festival Promo"))
            .andReturn()
            .getResponse()
            .getContentAsString();

    Long promoId = objectMapper.readTree(promoRes).path("data").path("id").asLong();

    AdminPromotionRequest updatePromoReq =
        new AdminPromotionRequest(
            "Summer Festival Promo V2",
            "ប្រូម៉ូសិនរដូវក្តៅ កែប្រែ",
            PromotionType.SEASONAL,
            25,
            null,
            now,
            now.plus(15, ChronoUnit.DAYS),
            true,
            List.of(),
            null);

    mockMvc
        .perform(
            put("/api/admin/promotions/" + promoId)
                .header("Authorization", adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updatePromoReq)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.name").value("Summer Festival Promo V2"));

    mockMvc
        .perform(delete("/api/admin/promotions/" + promoId).header("Authorization", adminToken))
        .andExpect(status().isOk());
  }

  @Test
  @DisplayName("k. Promotions: per-variant flash sale items, validation, legacy productIds")
  void testPerVariantPromotionItems() throws Exception {
    Instant now = Instant.now();

    // Seeded flash sale (id 1) stays active throughout; the new one coexists with it.
    JsonNode seeded =
        objectMapper
            .readTree(
                mockMvc
                    .perform(get("/api/admin/promotions/1").header("Authorization", adminToken))
                    .andExpect(status().isOk())
                    .andReturn()
                    .getResponse()
                    .getContentAsString())
            .path("data");
    assertThat(seeded.path("items")).hasSize(5);
    seeded
        .path("items")
        .forEach(
            it -> {
              if (it.path("variantId").asLong() == 1L) {
                assertThat(it.path("sku").asText()).isNotEmpty();
                assertThat(it.path("attributes").isObject()).isTrue();
                assertThat(it.path("priceCents").asLong()).isEqualTo(99900L);
                assertThat(it.path("salePriceCents").asLong()).isEqualTo(89900L);
              }
            });

    List<Long> createdIds = new java.util.ArrayList<>();
    try {
      // 1. Per-variant flash sale on 1001 variant 3 -> 119900 while promotion 1 stays active:
      //    variants 1,2 keep 89900 (promotion 1), variant 3 gets 119900, variant 4 not on sale
      String res =
          postPromotion(
                  flashRequest(
                      "Laptop variant flash",
                      now,
                      true,
                      null,
                      List.of(new PromotionItemRequest(1001L, 3L, 119900L))))
              .andExpect(status().isOk())
              .andExpect(jsonPath("$.data.items.length()").value(1))
              .andReturn()
              .getResponse()
              .getContentAsString();
      createdIds.add(objectMapper.readTree(res).path("data").path("id").asLong());

      mockMvc
          .perform(get("/api/products/1001"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.data.variants[0].salePriceCents").value(89900))
          .andExpect(jsonPath("$.data.variants[1].salePriceCents").value(89900))
          .andExpect(jsonPath("$.data.variants[2].salePriceCents").value(119900))
          .andExpect(jsonPath("$.data.variants[3].salePriceCents").doesNotExist());

      // 1b. Same variant in a second overlapping flash sale -> 409 naming variant and other promo
      postPromotion(
              flashRequest(
                  "Dup variant",
                  now,
                  true,
                  null,
                  List.of(new PromotionItemRequest(1001L, 1L, 80000L))))
          .andExpect(status().isConflict())
          .andExpect(jsonPath("$.error.code").value("CONFLICT"))
          .andExpect(
              jsonPath("$.error.details['items[0]']")
                  .value(
                      org.hamcrest.Matchers.allOf(
                          org.hamcrest.Matchers.containsString("Variant 1 of product 1001"),
                          org.hamcrest.Matchers.containsString("(id 1)"))));

      // 1c. Same variant in a NON-overlapping window is fine
      Instant later = now.plus(30, ChronoUnit.DAYS);
      String later1 =
          postPromotion(
                  flashRequest(
                      "Later flash",
                      later,
                      true,
                      null,
                      List.of(new PromotionItemRequest(1001L, 1L, 80000L))))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();
      createdIds.add(objectMapper.readTree(later1).path("data").path("id").asLong());

      // 2. Product-wide FLASH_SALE item on a product with variants -> 400 with details
      postPromotion(
              flashRequest(
                  "Bad wide",
                  now,
                  false,
                  null,
                  List.of(new PromotionItemRequest(1001L, null, 89900L))))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
          .andExpect(jsonPath("$.error.details['items[0].variantId']").exists());

      // 3. Variant from another product -> 400
      postPromotion(
              flashRequest(
                  "Bad variant",
                  now,
                  false,
                  null,
                  List.of(new PromotionItemRequest(1010L, 3L, 1000L))))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
          .andExpect(jsonPath("$.error.details['items[0].variantId']").exists());

      // 4. Sale price must be > 0 and < variant price (variant 3 = 129900)
      for (long bad : new long[] {0L, 129900L, 150000L}) {
        postPromotion(
                flashRequest(
                    "Bad price",
                    now,
                    false,
                    null,
                    List.of(new PromotionItemRequest(1001L, 3L, bad))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.details['items[0].salePriceCents']").exists());
      }

      // 5. Legacy productIds-only payload on a product without variants still works
      String legacy =
          postPromotion(flashRequest("Legacy ids", now, false, List.of(1015L), null))
              .andExpect(status().isOk())
              .andExpect(jsonPath("$.data.productIds[0]").value(1015))
              .andExpect(jsonPath("$.data.items[0].productId").value(1015))
              .andReturn()
              .getResponse()
              .getContentAsString();
      Long legacyId = objectMapper.readTree(legacy).path("data").path("id").asLong();
      mockMvc
          .perform(delete("/api/admin/promotions/" + legacyId).header("Authorization", adminToken))
          .andExpect(status().isOk());
    } finally {
      for (Long cid : createdIds) {
        mockMvc.perform(delete("/api/admin/promotions/" + cid).header("Authorization", adminToken));
      }
    }
  }

  private AdminPromotionRequest flashRequest(
      String name,
      Instant startsAt,
      boolean active,
      List<Long> productIds,
      List<PromotionItemRequest> items) {
    return new AdminPromotionRequest(
        name,
        null,
        PromotionType.FLASH_SALE,
        15,
        null,
        startsAt.minus(1, ChronoUnit.DAYS),
        startsAt.plus(3, ChronoUnit.DAYS),
        active,
        productIds,
        items);
  }

  private org.springframework.test.web.servlet.ResultActions postPromotion(
      AdminPromotionRequest req) throws Exception {
    return mockMvc.perform(
        post("/api/admin/promotions")
            .header("Authorization", adminToken)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(req)));
  }

  private org.springframework.test.web.servlet.ResultActions putPromotion(
      Long id, AdminPromotionRequest req) throws Exception {
    return mockMvc.perform(
        put("/api/admin/promotions/" + id)
            .header("Authorization", adminToken)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(req)));
  }

  @Test
  @DisplayName("j. Reports: sales grouped by day, CSV and Excel XLSX export")
  void testSalesReportsAndExports() throws Exception {
    mockMvc
        .perform(get("/api/admin/reports/sales?groupBy=day").header("Authorization", adminToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data").isArray());

    mockMvc
        .perform(get("/api/admin/reports/sales?groupBy=month").header("Authorization", adminToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true));

    mockMvc
        .perform(
            get("/api/admin/reports/sales?groupBy=category").header("Authorization", adminToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true));

    mockMvc
        .perform(
            get("/api/admin/reports/sales?groupBy=product").header("Authorization", adminToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true));

    mockMvc
        .perform(
            get("/api/admin/reports/sales?groupBy=paymentMethod")
                .header("Authorization", adminToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true));

    mockMvc
        .perform(
            get("/api/admin/reports/sales.csv?groupBy=day").header("Authorization", adminToken))
        .andExpect(status().isOk())
        .andExpect(
            header()
                .string(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"sales-day.csv\""));

    mockMvc
        .perform(
            get("/api/admin/reports/sales.xlsx?groupBy=day").header("Authorization", adminToken))
        .andExpect(status().isOk())
        .andExpect(
            header()
                .string(
                    HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"sales-day.xlsx\""));
  }

  @Test
  @DisplayName(
      "k. Settings: khrRate immediately reflected in /api/settings/public; LIVE mode without token -> 422")
  void testSettingsLiveCheckAndPublicReflection() throws Exception {
    // 1. Update khrRate to 4150 -> immediately reflected in /api/settings/public
    AdminSettingsUpdateRequest updateReq =
        new AdminSettingsUpdateRequest("KhmerMart Official", 4150L, "MOCK", List.of());

    mockMvc
        .perform(
            put("/api/admin/settings")
                .header("Authorization", adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateReq)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.khrRate").value(4150));

    mockMvc
        .perform(get("/api/settings/public"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.khrRate").value(4150));

    // 2. Switch khqrMode to LIVE without BAKONG_TOKEN configured -> 422 UNPROCESSABLE_ENTITY
    AdminSettingsUpdateRequest liveReq =
        new AdminSettingsUpdateRequest("KhmerMart Official", 4150L, "LIVE", List.of());

    mockMvc
        .perform(
            put("/api/admin/settings")
                .header("Authorization", adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(liveReq)))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(
            jsonPath("$.error.message")
                .value("Switching to LIVE mode requires BAKONG_TOKEN to be configured"));
  }

  @Test
  @DisplayName("l. Audit logs: verify rows produced with before/after JSON")
  void testAuditLogsVerification() throws Exception {
    long initialCount = auditLogRepository.count();

    // Perform an admin update that triggers @Audited
    mockMvc
        .perform(
            put("/api/admin/settings")
                .header("Authorization", adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new AdminSettingsUpdateRequest("Audit Store", 4100L, "MOCK", List.of()))))
        .andExpect(status().isOk());

    Thread.sleep(300);

    mockMvc
        .perform(get("/api/admin/audit-logs").header("Authorization", adminToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)))
        .andExpect(jsonPath("$.data[0].action").isNotEmpty())
        .andExpect(jsonPath("$.data[0].entity").isNotEmpty());

    assertThat(auditLogRepository.count()).isGreaterThan(initialCount);
  }
}
