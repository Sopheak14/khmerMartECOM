package com.khmermart.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.khmermart.AbstractIntegrationTest;
import com.khmermart.model.entity.Order;
import com.khmermart.model.entity.Payment;
import com.khmermart.model.enumerate.OrderStatus;
import com.khmermart.model.enumerate.PaidMode;
import com.khmermart.model.enumerate.PaymentMethod;
import com.khmermart.model.enumerate.PaymentStatus;
import com.khmermart.model.request.RegisterRequest;
import com.khmermart.repository.OrderRepository;
import com.khmermart.repository.PaymentRepository;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
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

/** BACKEND-REFUND-2: structured refund data on orders + admin payments orderNumber filter. */
class OrderRefundFieldsIntegrationTest extends AbstractIntegrationTest {

  private static final String PASSWORD = "Refund12345";
  private static final String PAYER = "payer@bkrt";

  @Autowired private TestRestTemplate rt;
  @Autowired private ObjectMapper om;
  @Autowired private OrderRepository orderRepository;
  @Autowired private PaymentRepository paymentRepository;

  private long customerId;
  private String customerToken;
  private String otherToken;
  private String staffToken;
  private String adminToken;

  @BeforeEach
  void users() throws Exception {
    String ce = "rf2-c-" + UUID.randomUUID() + "@test.local";
    customerToken = account(ce, "CUSTOMER");
    customerId = userId(ce);
    otherToken = account("rf2-o-" + UUID.randomUUID() + "@test.local", "CUSTOMER");
    staffToken = account("rf2-s-" + UUID.randomUUID() + "@test.local", "STAFF");
    adminToken = account("rf2-a-" + UUID.randomUUID() + "@test.local", "ADMIN");
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

  private record Fx(Order order, Payment payment) {}

  private Fx paidLiveOrder() {
    Order order =
        orderRepository.save(
            Order.builder()
                .orderNumber("KM-RF2-" + UUID.randomUUID().toString().substring(0, 10))
                .userId(customerId)
                .status(OrderStatus.CONFIRMED)
                .paymentStatus(PaymentStatus.PAID)
                .paymentMethod(PaymentMethod.KHQR)
                .subtotalCents(15L)
                .discountCents(0L)
                .shippingCents(0L)
                .totalCents(15L)
                .shippingAddress("{}")
                .shippingMethodCode("STANDARD")
                .placedAt(Instant.now())
                .build());
    Payment payment =
        paymentRepository.save(
            Payment.builder()
                .orderId(order.getId())
                .method(PaymentMethod.KHQR)
                .status(PaymentStatus.PAID)
                .amountCents(15L)
                .currency("USD")
                .paidMode(PaidMode.LIVE)
                .bakongHash("HASH" + UUID.randomUUID())
                .fromAccountId(PAYER)
                .paidAt(Instant.now())
                .createdAt(Instant.now())
                .build());
    return new Fx(order, payment);
  }

  private ResponseEntity<String> call(String token, HttpMethod m, String url, Object body) {
    HttpHeaders h = new HttpHeaders();
    h.setBearerAuth(token);
    return rt.exchange(url, m, new HttpEntity<>(body, h), String.class);
  }

  private JsonNode data(ResponseEntity<String> r) throws Exception {
    return om.readTree(r.getBody()).get("data");
  }

  private void cancelAndPend(Fx f) {
    ResponseEntity<String> r =
        call(
            staffToken,
            HttpMethod.POST,
            "/api/admin/orders/" + f.order().getOrderNumber() + "/cancel",
            Map.of("reason", "customer asked"));
    assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
  }

  @Test
  @DisplayName("Owner sees refund PENDING with masked payer; other customer gets 404")
  void ownerMaskedOtherNotFound() throws Exception {
    Fx f = paidLiveOrder();
    String n = f.order().getOrderNumber();

    // before any cancel: no refund object, cancelRequested false
    JsonNode before = data(call(customerToken, HttpMethod.GET, "/api/orders/" + n, null));
    assertThat(before.has("refund")).isFalse();
    assertThat(before.get("cancelRequested").asBoolean()).isFalse();

    // customer request → cancelRequested + reason + at visible
    call(customerToken, HttpMethod.POST, "/api/orders/" + n + "/cancel-request", Map.of("reason", "wrong size"));
    JsonNode req = data(call(customerToken, HttpMethod.GET, "/api/orders/" + n, null));
    assertThat(req.get("cancelRequested").asBoolean()).isTrue();
    assertThat(req.get("cancelRequestReason").asText()).isEqualTo("wrong size");
    assertThat(req.get("cancelRequestedAt").asText()).isNotBlank();

    cancelAndPend(f);

    JsonNode d = data(call(customerToken, HttpMethod.GET, "/api/orders/" + n, null));
    JsonNode refund = d.get("refund");
    assertThat(refund.get("status").asText()).isEqualTo("PENDING");
    assertThat(refund.get("amountCents").asLong()).isEqualTo(15L);
    assertThat(refund.get("payerAccount").asText()).isEqualTo("****bkrt");
    assertThat(refund.get("bankReference").isNull()).isTrue();
    assertThat(refund.get("completedAt").isNull()).isTrue();
    assertThat(d.has("paymentId")).isFalse(); // admin-only

    // list item carries the same masked object
    JsonNode list = data(call(customerToken, HttpMethod.GET, "/api/orders?size=50", null));
    JsonNode item = null;
    for (JsonNode o : list) {
      if (n.equals(o.get("orderNumber").asText())) {
        item = o;
      }
    }
    assertThat(item).isNotNull();
    assertThat(item.get("refund").get("payerAccount").asText()).isEqualTo("****bkrt");

    // someone else's order → 404, not 403
    assertThat(call(otherToken, HttpMethod.GET, "/api/orders/" + n, null).getStatusCode())
        .isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  @DisplayName("Admin order detail: unmasked payer + paymentId; COMPLETED shows bank ref")
  void adminUnmaskedAndCompleted() throws Exception {
    Fx f = paidLiveOrder();
    String n = f.order().getOrderNumber();
    cancelAndPend(f);

    for (String token : new String[] {adminToken, staffToken}) {
      JsonNode a = data(call(token, HttpMethod.GET, "/api/admin/orders/" + n, null));
      assertThat(a.get("paymentId").asLong()).isEqualTo(f.payment().getId());
      assertThat(a.get("refund").get("status").asText()).isEqualTo("PENDING");
      assertThat(a.get("refund").get("payerAccount").asText()).isEqualTo(PAYER);
    }

    ResponseEntity<String> done =
        call(
            adminToken,
            HttpMethod.POST,
            "/api/admin/payments/" + f.payment().getId() + "/refund/complete",
            Map.of("bankReference", "ABA-12345", "note", "sent"));
    assertThat(done.getStatusCode()).isEqualTo(HttpStatus.OK);

    JsonNode c = data(call(customerToken, HttpMethod.GET, "/api/orders/" + n, null)).get("refund");
    assertThat(c.get("status").asText()).isEqualTo("COMPLETED");
    assertThat(c.get("bankReference").asText()).isEqualTo("ABA-12345");
    assertThat(c.get("completedAt").asText()).isNotBlank();
    assertThat(c.get("payerAccount").asText()).isEqualTo("****bkrt");

    JsonNode a = data(call(adminToken, HttpMethod.GET, "/api/admin/orders/" + n, null)).get("refund");
    assertThat(a.get("status").asText()).isEqualTo("COMPLETED");
    assertThat(a.get("payerAccount").asText()).isEqualTo(PAYER);
  }

  @Test
  @DisplayName("GET /admin/payments?orderNumber= is an exact match; unknown → empty")
  void paymentsOrderNumberFilter() throws Exception {
    Fx a = paidLiveOrder();
    paidLiveOrder(); // a second order that must not match
    String n = a.order().getOrderNumber();

    JsonNode hit = data(call(adminToken, HttpMethod.GET, "/api/admin/payments?orderNumber=" + n, null));
    assertThat(hit).hasSize(1);
    assertThat(hit.get(0).get("id").asLong()).isEqualTo(a.payment().getId());
    assertThat(hit.get(0).get("orderNumber").asText()).isEqualTo(n);

    // combines with status
    cancelAndPend(a);
    JsonNode pending =
        data(
            call(
                adminToken,
                HttpMethod.GET,
                "/api/admin/payments?orderNumber=" + n + "&status=REFUND_PENDING",
                null));
    assertThat(pending).hasSize(1);
    assertThat(
            data(
                call(
                    adminToken,
                    HttpMethod.GET,
                    "/api/admin/payments?orderNumber=" + n + "&status=PAID",
                    null)))
        .isEmpty();

    // partial and unknown numbers do not match
    String partial = n.substring(0, n.length() - 2);
    assertThat(data(call(adminToken, HttpMethod.GET, "/api/admin/payments?orderNumber=" + partial, null)))
        .isEmpty();
    assertThat(data(call(adminToken, HttpMethod.GET, "/api/admin/payments?orderNumber=KM-NOPE", null)))
        .isEmpty();
  }
}
