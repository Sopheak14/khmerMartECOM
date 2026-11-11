package com.khmermart.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.khmermart.AbstractIntegrationTest;
import com.khmermart.model.entity.Inventory;
import com.khmermart.model.entity.Order;
import com.khmermart.model.entity.OrderItem;
import com.khmermart.model.entity.Payment;
import com.khmermart.model.entity.Product;
import com.khmermart.model.enumerate.OrderStatus;
import com.khmermart.model.enumerate.PaidMode;
import com.khmermart.model.enumerate.PaymentMethod;
import com.khmermart.model.enumerate.PaymentStatus;
import com.khmermart.model.request.RegisterRequest;
import com.khmermart.repository.InventoryRepository;
import com.khmermart.repository.OrderItemRepository;
import com.khmermart.repository.OrderRepository;
import com.khmermart.repository.PaymentRepository;
import com.khmermart.repository.ProductRepository;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * BACKEND-REFUND-1: a KHQR (LIVE) refund is a manual bank transfer, so the system must say
 * REFUND_PENDING until an ADMIN records the bank reference. Throwaway accounts only; LIVE is
 * simulated by inserting payment rows with paidMode=LIVE (the real KHQR setting is never touched).
 */
class HonestRefundIntegrationTest extends AbstractIntegrationTest {

  private static final String PASSWORD = "Refund12345";
  private static final String PAYER = "payer" + "@bkrt";

  @Autowired private TestRestTemplate rt;
  @Autowired private ObjectMapper om;
  @Autowired private OrderRepository orderRepository;
  @Autowired private OrderItemRepository orderItemRepository;
  @Autowired private PaymentRepository paymentRepository;
  @Autowired private InventoryRepository inventoryRepository;
  @Autowired private ProductRepository productRepository;

  private String customerEmail;
  private long customerId;
  private String customerToken;
  private String staffToken;
  private String adminToken;
  private long adminId;

  @BeforeEach
  void users() throws Exception {
    customerEmail = "refund-c-" + UUID.randomUUID() + "@test.local";
    customerToken = account(customerEmail, "CUSTOMER");
    customerId = userId(customerEmail);
    String staffEmail = "refund-s-" + UUID.randomUUID() + "@test.local";
    staffToken = account(staffEmail, "STAFF");
    String adminEmail = "refund-a-" + UUID.randomUUID() + "@test.local";
    adminToken = account(adminEmail, "ADMIN");
    adminId = userId(adminEmail);
  }

  private String account(String email, String role) throws Exception {
    ResponseEntity<String> r =
        rt.postForEntity(
            "/api/auth/register",
            new RegisterRequest(email, PASSWORD, "Refund Tester", "+85512345678"),
            String.class);
    assertThat(r.getStatusCode().is2xxSuccessful()).isTrue();
    markVerified(email);
    jdbc.update("UPDATE users SET role = ? WHERE email = ?", role, email.toLowerCase());
    ResponseEntity<String> l =
        rt.postForEntity(
            "/api/auth/login", Map.of("email", email, "password", PASSWORD), String.class);
    return om.readTree(l.getBody()).get("data").get("accessToken").asText();
  }

  // ------------------------------------------------------------------ helpers

  private record Fixture(Order order, Payment payment, Inventory inventory) {}

  /** An order of 1 x 15-cent product. paid=true → stock on hand 10; unpaid → 1 reserved. */
  private Fixture fixture(
      OrderStatus status,
      PaymentStatus payStatus,
      PaymentMethod method,
      PaidMode mode,
      String fromAccount) {
    Product product =
        productRepository.save(
            Product.builder()
                .sku("RF-" + UUID.randomUUID().toString().substring(0, 8))
                .name("Refund Test Product")
                .slug("refund-test-" + UUID.randomUUID())
                .priceCents(15L)
                .active(true)
                .build());
    boolean paid = payStatus == PaymentStatus.PAID;
    Inventory inv =
        inventoryRepository.save(
            Inventory.builder()
                .productId(product.getId())
                .quantityOnHand(10)
                .quantityReserved(paid ? 0 : 1)
                .lowStockThreshold(1)
                .build());
    Order order =
        orderRepository.save(
            Order.builder()
                .orderNumber("KM-RF-" + UUID.randomUUID().toString().substring(0, 10))
                .userId(customerId)
                .status(status)
                .paymentStatus(payStatus)
                .paymentMethod(method)
                .subtotalCents(15L)
                .discountCents(0L)
                .shippingCents(0L)
                .totalCents(15L)
                .shippingAddress("{}")
                .shippingMethodCode("STANDARD")
                .placedAt(Instant.now())
                .build());
    orderItemRepository.save(
        OrderItem.builder()
            .orderId(order.getId())
            .productId(product.getId())
            .productNameSnapshot("Refund Test Product")
            .skuSnapshot(product.getSku())
            .unitPriceCents(15L)
            .quantity(1)
            .lineTotalCents(15L)
            .build());
    Payment payment =
        paymentRepository.save(
            Payment.builder()
                .orderId(order.getId())
                .method(method)
                .status(payStatus)
                .amountCents(15L)
                .currency("USD")
                .paidMode(mode)
                .bakongHash(
                    mode == PaidMode.MOCK
                        ? "MOCK-" + UUID.randomUUID()
                        : mode == PaidMode.LIVE ? "HASH" + UUID.randomUUID() : null)
                .fromAccountId(fromAccount)
                .paidAt(paid ? Instant.now() : null)
                .createdAt(Instant.now())
                .build());
    return new Fixture(order, payment, inv);
  }

  private Fixture paid(PaidMode mode) {
    return fixture(
        OrderStatus.CONFIRMED,
        PaymentStatus.PAID,
        mode == PaidMode.COD ? PaymentMethod.COD : PaymentMethod.KHQR,
        mode,
        mode == PaidMode.LIVE ? PAYER : null);
  }

  private ResponseEntity<String> call(String token, HttpMethod m, String url, Object body) {
    HttpHeaders h = new HttpHeaders();
    h.setBearerAuth(token);
    return rt.exchange(url, m, new HttpEntity<>(body, h), String.class);
  }

  private JsonNode json(ResponseEntity<String> r) throws Exception {
    return om.readTree(r.getBody());
  }

  private String code(ResponseEntity<String> r) throws Exception {
    return json(r).get("error").get("code").asText();
  }

  private List<String> timeline(Order o) {
    return jdbc.queryForList(
        "SELECT note FROM order_status_history WHERE order_id = ? ORDER BY id",
        String.class,
        o.getId());
  }

  private Map<String, Object> state(Fixture f) {
    return jdbc.queryForMap(
        "SELECT o.status AS ostatus, o.payment_status AS opay, p.status AS pstatus,"
            + " o.cancel_requested AS req, o.needs_attention AS attn, i.quantity_on_hand AS onhand,"
            + " i.quantity_reserved AS reserved"
            + " FROM orders o JOIN payments p ON p.order_id = o.id"
            + " JOIN inventory i ON i.id = ? WHERE o.id = ?",
        f.inventory().getId(),
        f.order().getId());
  }

  private List<Map<String, Object>> refunds(Fixture f) {
    return jdbc.queryForList("SELECT * FROM refunds WHERE payment_id = ?", f.payment().getId());
  }

  private int audit(String action, String entityId) {
    return jdbc.queryForObject(
        "SELECT count(*) FROM audit_logs WHERE action = ? AND entity_id = ?"
            + " AND before_json IS NOT NULL AND after_json IS NOT NULL",
        Integer.class,
        action,
        entityId);
  }

  private String notificationBody(String type) throws Exception {
    for (int i = 0; i < 50; i++) {
      List<String> rows =
          jdbc.queryForList(
              "SELECT body FROM notifications WHERE user_id = ? AND type = ? AND channel = 'IN_APP'",
              String.class,
              customerId,
              type);
      if (!rows.isEmpty()) {
        return rows.get(0);
      }
      Thread.sleep(200);
    }
    return null;
  }

  private String cancelUrl(Fixture f) {
    return "/api/admin/orders/" + f.order().getOrderNumber() + "/cancel";
  }

  // -------------------------------------------------------------- customer rules

  @Test
  @DisplayName("Customer cancels an UNPAID order directly: CANCELLED, stock RELEASE, no refund")
  void customerCancelUnpaid() throws Exception {
    Fixture f =
        fixture(OrderStatus.PENDING_PAYMENT, PaymentStatus.PENDING, PaymentMethod.KHQR, null, null);
    ResponseEntity<String> r =
        call(
            customerToken,
            HttpMethod.POST,
            "/api/orders/" + f.order().getOrderNumber() + "/cancel",
            Map.of("reason", "changed my mind"));
    assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
    Map<String, Object> s = state(f);
    assertThat(s.get("ostatus")).isEqualTo("CANCELLED");
    assertThat(s.get("pstatus")).isEqualTo("PENDING");
    assertThat(s.get("reserved")).isEqualTo(0);
    assertThat(s.get("onhand")).isEqualTo(10);
    assertThat(refunds(f)).isEmpty();
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM inventory_movements WHERE ref_order_id = ? AND type = 'RELEASE'",
                Integer.class,
                f.order().getId()))
        .isEqualTo(1);
  }

  @Test
  @DisplayName("Customer cancelling a PAID order directly is refused: must use cancel-request")
  void customerCancelPaidMustUseRequest() throws Exception {
    Fixture f = paid(PaidMode.LIVE);
    ResponseEntity<String> r =
        call(
            customerToken,
            HttpMethod.POST,
            "/api/orders/" + f.order().getOrderNumber() + "/cancel",
            Map.of("reason", "nope"));
    assertThat(r.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(code(r)).isEqualTo("CANCEL_NOT_ALLOWED");
    assertThat(state(f).get("ostatus")).isEqualTo("CONFIRMED");
    assertThat(state(f).get("pstatus")).isEqualTo("PAID");
  }

  @Test
  @DisplayName("cancel-request: flags the order, status unchanged; 2nd request 409; validation")
  void cancelRequestAndDuplicate() throws Exception {
    Fixture f = paid(PaidMode.LIVE);
    String url = "/api/orders/" + f.order().getOrderNumber() + "/cancel-request";

    assertThat(call(customerToken, HttpMethod.POST, url, Map.of("reason", " ")).getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(
            call(customerToken, HttpMethod.POST, url, Map.of("reason", "x".repeat(501)))
                .getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);

    ResponseEntity<String> r =
        call(customerToken, HttpMethod.POST, url, Map.of("reason", "ordered by mistake"));
    assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(json(r).get("data").get("cancelRequested").asBoolean()).isTrue();
    Map<String, Object> s = state(f);
    assertThat(s.get("ostatus")).isEqualTo("CONFIRMED");
    assertThat(s.get("req")).isEqualTo(true);
    assertThat(s.get("attn")).isEqualTo(true);
    assertThat(timeline(f.order()))
        .containsExactly("Cancellation requested by customer: ordered by mistake");

    ResponseEntity<String> again =
        call(customerToken, HttpMethod.POST, url, Map.of("reason", "again"));
    assertThat(again.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(code(again)).isEqualTo("CANCEL_ALREADY_REQUESTED");
    assertThat(audit("CUSTOMER_CANCEL_REQUEST", f.order().getOrderNumber())).isEqualTo(1);
  }

  @Test
  @DisplayName("SHIPPED / OUT_FOR_DELIVERY / DELIVERED: cancel and cancel-request → 409")
  void shippedCannotBeCancelled() throws Exception {
    for (OrderStatus st :
        List.of(OrderStatus.SHIPPED, OrderStatus.OUT_FOR_DELIVERY, OrderStatus.DELIVERED)) {
      Fixture f = fixture(st, PaymentStatus.PAID, PaymentMethod.KHQR, PaidMode.LIVE, PAYER);
      for (String path : List.of("/cancel", "/cancel-request")) {
        ResponseEntity<String> r =
            call(
                customerToken,
                HttpMethod.POST,
                "/api/orders/" + f.order().getOrderNumber() + path,
                Map.of("reason", "too late"));
        assertThat(r.getStatusCode()).as(st + path).isEqualTo(HttpStatus.CONFLICT);
        assertThat(code(r)).isEqualTo("CANCEL_NOT_ALLOWED");
      }
      ResponseEntity<String> admin =
          call(adminToken, HttpMethod.POST, cancelUrl(f), Map.of("reason", "late"));
      assertThat(admin.getStatusCode()).as("admin " + st).isEqualTo(HttpStatus.CONFLICT);
      assertThat(code(admin)).isEqualTo("CANCEL_NOT_ALLOWED");
    }
  }

  // ---------------------------------------------------------------- admin rules

  @Test
  @DisplayName(
      "Admin approves cancel of LIVE payment → REFUND_PENDING (not REFUNDED), stock RETURN")
  void adminApproveLive() throws Exception {
    Fixture f = paid(PaidMode.LIVE);
    call(
        customerToken,
        HttpMethod.POST,
        "/api/orders/" + f.order().getOrderNumber() + "/cancel-request",
        Map.of("reason", "wrong size"));

    // STAFF may approve
    ResponseEntity<String> r =
        call(staffToken, HttpMethod.POST, cancelUrl(f), Map.of("reason", "approved"));
    assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(json(r).get("data").get("paymentStatus").asText()).isEqualTo("REFUND_PENDING");

    Map<String, Object> s = state(f);
    assertThat(s.get("ostatus")).isEqualTo("CANCELLED");
    assertThat(s.get("opay")).isEqualTo("REFUND_PENDING");
    assertThat(s.get("pstatus")).isEqualTo("REFUND_PENDING");
    assertThat(s.get("req")).isEqualTo(false);
    assertThat(s.get("attn")).isEqualTo(false);
    assertThat(s.get("onhand")).isEqualTo(11); // RETURN
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM inventory_movements WHERE ref_order_id = ? AND type = 'RETURN'",
                Integer.class,
                f.order().getId()))
        .isEqualTo(1);

    List<Map<String, Object>> rf = refunds(f);
    assertThat(rf).hasSize(1);
    assertThat(rf.get(0).get("status")).isEqualTo("PENDING");
    assertThat(rf.get(0).get("payer_account")).isEqualTo(PAYER);
    assertThat(rf.get(0).get("amount_cents")).isEqualTo(15L);
    assertThat(rf.get(0).get("bank_reference")).isNull();

    // exact wording, no duplicate rows
    assertThat(timeline(f.order()))
        .containsExactly(
            "Cancellation requested by customer: wrong size",
            "Cancelled by admin: approved",
            "Refund pending: $0.15 to " + PAYER + " (manual bank transfer)");
    assertThat(audit("ADMIN_CANCEL_ORDER", f.order().getOrderNumber())).isEqualTo(1);

    String body = notificationBody("ORDER_CANCELLED");
    assertThat(body)
        .contains("We will send $0.15 back to your account " + PAYER + " within 3 working days.")
        .contains("យើងនឹងផ្ទេរប្រាក់ $0.15 ត្រឡប់ទៅគណនី " + PAYER + " ក្នុងរយៈពេល ៣ ថ្ងៃធ្វើការ។");

    // cancelling again is refused
    assertThat(
            call(adminToken, HttpMethod.POST, cancelUrl(f), Map.of("reason", "x")).getStatusCode())
        .isEqualTo(HttpStatus.CONFLICT);
  }

  @Test
  @DisplayName("Admin approves cancel of MOCK payment → REFUNDED immediately, COMPLETED refund")
  void adminApproveMock() throws Exception {
    Fixture f = paid(PaidMode.MOCK);
    ResponseEntity<String> r =
        call(adminToken, HttpMethod.POST, cancelUrl(f), Map.of("reason", "test order"));
    assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
    Map<String, Object> s = state(f);
    assertThat(s.get("pstatus")).isEqualTo("REFUNDED");
    assertThat(s.get("opay")).isEqualTo("REFUNDED");
    assertThat(s.get("onhand")).isEqualTo(11);
    Map<String, Object> rf = refunds(f).get(0);
    assertThat(rf.get("status")).isEqualTo("COMPLETED");
    assertThat(rf.get("note")).isEqualTo("MOCK payment — no real money moved");
    assertThat(timeline(f.order()))
        .containsExactly("Cancelled by admin: test order", "Refunded (MOCK — no real money moved)");
  }

  @Test
  @DisplayName("Admin cancels COD order whose cash was collected → REFUND_PENDING")
  void adminCancelCodCollected() throws Exception {
    Fixture f = paid(PaidMode.COD);
    call(adminToken, HttpMethod.POST, cancelUrl(f), Map.of("reason", "returned"));
    assertThat(state(f).get("pstatus")).isEqualTo("REFUND_PENDING");
    assertThat(refunds(f).get(0).get("status")).isEqualTo("PENDING");
  }

  @Test
  @DisplayName("Admin cancels an UNPAID order → stock RELEASE, no refund; legacy PATCH status same")
  void adminCancelUnpaidAndLegacyPatch() throws Exception {
    Fixture f =
        fixture(OrderStatus.PENDING_PAYMENT, PaymentStatus.PENDING, PaymentMethod.KHQR, null, null);
    call(adminToken, HttpMethod.POST, cancelUrl(f), Map.of("reason", "stale"));
    Map<String, Object> s = state(f);
    assertThat(s.get("ostatus")).isEqualTo("CANCELLED");
    assertThat(s.get("reserved")).isEqualTo(0);
    assertThat(s.get("onhand")).isEqualTo(10);
    assertThat(refunds(f)).isEmpty();
    assertThat(timeline(f.order())).containsExactly("Cancelled by admin: stale");

    // the old PATCH /status CANCELLED path must not bypass the honest flow
    Fixture live = paid(PaidMode.LIVE);
    ResponseEntity<String> r =
        call(
            adminToken,
            HttpMethod.PATCH,
            "/api/admin/orders/" + live.order().getOrderNumber() + "/status",
            Map.of("status", "CANCELLED", "note", "via patch"));
    assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(state(live).get("pstatus")).isEqualTo("REFUND_PENDING");
  }

  @Test
  @DisplayName("Reject cancel request: flags cleared, customer told the reason; no request → 409")
  void rejectRequest() throws Exception {
    Fixture f = paid(PaidMode.LIVE);
    String reject = "/api/admin/orders/" + f.order().getOrderNumber() + "/cancel-request/reject";
    assertThat(call(adminToken, HttpMethod.POST, reject, Map.of("reason", "x")).getStatusCode())
        .isEqualTo(HttpStatus.CONFLICT);

    call(
        customerToken,
        HttpMethod.POST,
        "/api/orders/" + f.order().getOrderNumber() + "/cancel-request",
        Map.of("reason", "changed mind"));
    assertThat(state(f).get("attn")).isEqualTo(true);

    ResponseEntity<String> r =
        call(staffToken, HttpMethod.POST, reject, Map.of("reason", "already packed"));
    assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
    Map<String, Object> s = state(f);
    assertThat(s.get("req")).isEqualTo(false);
    assertThat(s.get("attn")).isEqualTo(false);
    assertThat(s.get("ostatus")).isEqualTo("CONFIRMED");
    assertThat(s.get("pstatus")).isEqualTo("PAID");
    assertThat(timeline(f.order()))
        .containsExactly(
            "Cancellation requested by customer: changed mind",
            "Cancellation rejected by admin: already packed");
    assertThat(notificationBody("CANCEL_REQUEST_REJECTED")).contains("already packed");
    assertThat(audit("ADMIN_REJECT_CANCEL_REQUEST", f.order().getOrderNumber())).isEqualTo(1);
  }

  // ------------------------------------------------------------- refund complete

  @Test
  @DisplayName("refund/complete: ADMIN only, bankReference 3-64, only from REFUND_PENDING")
  void completeRefund() throws Exception {
    Fixture f = paid(PaidMode.LIVE);
    call(adminToken, HttpMethod.POST, cancelUrl(f), Map.of("reason", "refund me"));
    long pid = f.payment().getId();
    String url = "/api/admin/payments/" + pid + "/refund/complete";

    // STAFF and customers are refused
    assertThat(
            call(staffToken, HttpMethod.POST, url, Map.of("bankReference", "ABA-12345"))
                .getStatusCode())
        .isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(
            call(customerToken, HttpMethod.POST, url, Map.of("bankReference", "ABA-12345"))
                .getStatusCode())
        .isEqualTo(HttpStatus.FORBIDDEN);

    // bankReference is required and 3-64 chars
    assertThat(call(adminToken, HttpMethod.POST, url, Map.of("note", "n")).getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(
            call(adminToken, HttpMethod.POST, url, Map.of("bankReference", "ab")).getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(
            call(adminToken, HttpMethod.POST, url, Map.of("bankReference", "r".repeat(65)))
                .getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(state(f).get("pstatus")).isEqualTo("REFUND_PENDING");

    // the admin filter finds it
    ResponseEntity<String> list =
        call(adminToken, HttpMethod.GET, "/api/admin/payments?status=REFUND_PENDING", null);
    assertThat(list.getStatusCode()).isEqualTo(HttpStatus.OK);
    boolean listed = false;
    for (JsonNode n : json(list).get("data")) {
      if (n.get("id").asLong() == pid) {
        listed = true;
        assertThat(n.get("refund").get("status").asText()).isEqualTo("PENDING");
        assertThat(n.get("refund").get("payerAccount").asText()).isEqualTo(PAYER);
      }
    }
    assertThat(listed).isTrue();

    ResponseEntity<String> ok =
        call(
            adminToken,
            HttpMethod.POST,
            url,
            Map.of("bankReference", "ABA-12345", "note", "sent from shop ABA"));
    assertThat(ok.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(json(ok).get("data").get("status").asText()).isEqualTo("REFUNDED");

    Map<String, Object> s = state(f);
    assertThat(s.get("pstatus")).isEqualTo("REFUNDED");
    assertThat(s.get("opay")).isEqualTo("REFUNDED");
    Map<String, Object> rf = refunds(f).get(0);
    assertThat(rf.get("status")).isEqualTo("COMPLETED");
    assertThat(rf.get("bank_reference")).isEqualTo("ABA-12345");
    assertThat(rf.get("completed_by")).isEqualTo(adminId);
    assertThat(rf.get("completed_at")).isNotNull();
    assertThat(rf.get("note")).isEqualTo("sent from shop ABA");
    assertThat(timeline(f.order()))
        .containsExactly(
            "Cancelled by admin: refund me",
            "Refund pending: $0.15 to " + PAYER + " (manual bank transfer)",
            "Refunded: $0.15 · bank ref ABA-12345");
    assertThat(audit("COMPLETE_REFUND", String.valueOf(pid))).isEqualTo(1);
    assertThat(notificationBody("REFUND_COMPLETED")).contains("ABA-12345");

    // wrong state now → 409 INVALID_REFUND_STATE
    ResponseEntity<String> again =
        call(adminToken, HttpMethod.POST, url, Map.of("bankReference", "ABA-99999"));
    assertThat(again.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(code(again)).isEqualTo("INVALID_REFUND_STATE");
  }

  @Test
  @DisplayName("refund/complete on a PAID payment → 409 INVALID_REFUND_STATE")
  void completeRefundWrongState() throws Exception {
    Fixture f = paid(PaidMode.LIVE);
    ResponseEntity<String> r =
        call(
            adminToken,
            HttpMethod.POST,
            "/api/admin/payments/" + f.payment().getId() + "/refund/complete",
            Map.of("bankReference", "ABA-12345"));
    assertThat(r.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(code(r)).isEqualTo("INVALID_REFUND_STATE");
  }

  // ------------------------------------------------------------------ migration

  @Test
  @DisplayName("V8 data fix: old LIVE 'REFUNDED' without a bank reference → REFUND_PENDING")
  void migrationDataFix() throws Exception {
    String db = "v8_migration_" + UUID.randomUUID().toString().substring(0, 8);
    String url = postgres.getJdbcUrl().replaceFirst("/[^/?]+(\\?|$)", "/" + db + "$1");
    try (Connection c =
            DriverManager.getConnection(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        Statement st = c.createStatement()) {
      st.execute("CREATE DATABASE " + db);
    }
    Flyway.configure()
        .dataSource(url, postgres.getUsername(), postgres.getPassword())
        .target("7")
        .load()
        .migrate();
    try (Connection c =
            DriverManager.getConnection(url, postgres.getUsername(), postgres.getPassword());
        Statement st = c.createStatement()) {
      st.execute(
          "INSERT INTO users (email, password_hash, full_name, phone, role, created_at, updated_at)"
              + " VALUES ('m@test.local','x','M','+85511111111','CUSTOMER', now(), now())");
      for (String[] o :
          new String[][] {
            {"KM-OLD-LIVE", "HASHLIVE1"}, {"KM-OLD-MOCK", "MOCK-ABC"}, {"KM-OLD-LIVE2", "HASHLIVE2"}
          }) {
        st.execute(
            "INSERT INTO orders (order_number, user_id, status, payment_status, payment_method,"
                + " subtotal_cents, total_cents, shipping_address, shipping_method_code, placed_at)"
                + " VALUES ('"
                + o[0]
                + "', (SELECT id FROM users LIMIT 1), 'CANCELLED', 'REFUNDED', 'KHQR', 15, 15,"
                + " '{}', 'STANDARD', now())");
        st.execute(
            "INSERT INTO payments (order_id, method, status, amount_cents, bakong_hash,"
                + " from_account_id, paid_at, created_at) VALUES ((SELECT id FROM orders WHERE"
                + " order_number='"
                + o[0]
                + "'), 'KHQR', 'REFUNDED', 15, '"
                + o[1]
                + "', 'payer@bkrt', now(), now())");
      }
      // KM-OLD-LIVE and KM-OLD-MOCK had the old fake COMPLETED refund; LIVE2 had none at all
      st.execute(
          "INSERT INTO refunds (payment_id, amount_cents, reason, reference, status, created_at)"
              + " SELECT p.id, 15, 'Order cancelled', 'REFUND-ab12cd34', 'COMPLETED', now()"
              + " FROM payments p JOIN orders o ON o.id = p.order_id"
              + " WHERE o.order_number IN ('KM-OLD-LIVE', 'KM-OLD-MOCK')");
    }
    Flyway.configure()
        .dataSource(url, postgres.getUsername(), postgres.getPassword())
        .load()
        .migrate();
    try (Connection c =
            DriverManager.getConnection(url, postgres.getUsername(), postgres.getPassword());
        Statement st = c.createStatement()) {
      var rs =
          st.executeQuery(
              "SELECT o.order_number, o.payment_status AS ops, p.status AS ps, p.paid_mode,"
                  + " (SELECT string_agg(r.status || ':' || coalesce(r.payer_account,''), ',')"
                  + "   FROM refunds r WHERE r.payment_id = p.id) AS refunds,"
                  + " (SELECT count(*) FROM order_status_history h WHERE h.order_id = o.id"
                  + "   AND h.note LIKE 'Refund pending: $0.15 to payer@bkrt%') AS notes"
                  + " FROM orders o JOIN payments p ON p.order_id = o.id ORDER BY o.order_number");
      java.util.Map<String, String[]> got = new java.util.HashMap<>();
      while (rs.next()) {
        got.put(
            rs.getString(1),
            new String[] {
              rs.getString("ops"),
              rs.getString("ps"),
              rs.getString("paid_mode"),
              rs.getString("refunds"),
              rs.getString("notes")
            });
      }
      assertThat(got.get("KM-OLD-LIVE"))
          .containsExactly("REFUND_PENDING", "REFUND_PENDING", "LIVE", "PENDING:payer@bkrt", "1");
      assertThat(got.get("KM-OLD-LIVE2"))
          .containsExactly("REFUND_PENDING", "REFUND_PENDING", "LIVE", "PENDING:payer@bkrt", "1");
      // MOCK payments really moved no money: stay REFUNDED, untouched
      assertThat(got.get("KM-OLD-MOCK"))
          .containsExactly("REFUNDED", "REFUNDED", "MOCK", "COMPLETED:", "0");
    }
  }
}
