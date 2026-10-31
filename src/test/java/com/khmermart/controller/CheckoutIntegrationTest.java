package com.khmermart.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.khmermart.AbstractIntegrationTest;
import com.khmermart.model.entity.Product;
import com.khmermart.model.enumerate.PaymentMethod;
import com.khmermart.model.request.AuthRequest;
import com.khmermart.model.request.CartItemRequest;
import com.khmermart.model.request.CheckoutRequest;
import com.khmermart.repository.ProductRepository;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class CheckoutIntegrationTest extends AbstractIntegrationTest {

  @Autowired private TestRestTemplate restTemplate;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private ProductRepository productRepository;

  private String loginCustomer(String email, String password) throws Exception {
    AuthRequest req = new AuthRequest(email, password);
    ResponseEntity<String> res = restTemplate.postForEntity("/api/auth/login", req, String.class);
    return objectMapper.readTree(res.getBody()).get("data").get("accessToken").asText();
  }

  @Test
  @DisplayName("1. Shipping methods: GET /api/shipping/methods and /api/shipping/provinces")
  void testShippingEndpoints() throws Exception {
    ResponseEntity<String> methodsRes =
        restTemplate.getForEntity("/api/shipping/methods", String.class);
    assertThat(methodsRes.getStatusCode()).isEqualTo(HttpStatus.OK);
    JsonNode methods = objectMapper.readTree(methodsRes.getBody()).get("data");
    assertThat(methods.size()).isGreaterThanOrEqualTo(3);
    assertThat(methods.get(0).get("code").asText()).isEqualTo("STANDARD");
    assertThat(methods.get(0).get("feeCents").asLong()).isEqualTo(150L);

    ResponseEntity<String> provRes =
        restTemplate.getForEntity("/api/shipping/provinces", String.class);
    assertThat(provRes.getStatusCode()).isEqualTo(HttpStatus.OK);
    JsonNode provinces = objectMapper.readTree(provRes.getBody()).get("data");
    assertThat(provinces.size()).isEqualTo(25);
  }

  @Test
  @DisplayName(
      "2. Checkout preview: Siem Reap address with SAME_DAY returns 400 VALIDATION_ERROR with details")
  void testSameDayShippingValidation() throws Exception {
    // Customer 2 (Dara Khiev) has address 3 located in Siem Reap
    String token = loginCustomer("customer2@khmermart.local", "Customer@123");
    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth(token);

    // Add item to cart first
    restTemplate.exchange(
        "/api/cart/items",
        HttpMethod.POST,
        new HttpEntity<>(new CartItemRequest(1001L, null, 1), headers),
        String.class);

    // Preview with address 3 (Siem Reap) and SAME_DAY
    CheckoutRequest req = new CheckoutRequest(3L, "SAME_DAY", PaymentMethod.COD, "Urgent order");
    ResponseEntity<String> res =
        restTemplate.exchange(
            "/api/checkout/preview", HttpMethod.POST, new HttpEntity<>(req, headers), String.class);

    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    JsonNode json = objectMapper.readTree(res.getBody());
    assertThat(json.get("success").asBoolean()).isFalse();
    assertThat(json.get("error").get("code").asText()).isEqualTo("VALIDATION_ERROR");
    assertThat(json.get("error").get("details").get("shippingMethodCode").asText())
        .isEqualTo("Same-day delivery is only available in Phnom Penh");
  }

  @Test
  @DisplayName(
      "3. Checkout preview: Phnom Penh address returns totals, totalKhr, items, shippingMethod")
  void testSuccessfulCheckoutPreview() throws Exception {
    // Customer 1 has address 1 located in Phnom Penh
    String token = loginCustomer("customer1@khmermart.local", "Customer@123");
    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth(token);

    // Ensure item in cart
    restTemplate.exchange(
        "/api/cart/items",
        HttpMethod.POST,
        new HttpEntity<>(new CartItemRequest(1001L, null, 1), headers),
        String.class);

    CheckoutRequest req = new CheckoutRequest(1L, "STANDARD", PaymentMethod.COD, "Leave at door");
    ResponseEntity<String> res =
        restTemplate.exchange(
            "/api/checkout/preview", HttpMethod.POST, new HttpEntity<>(req, headers), String.class);

    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
    JsonNode preview = objectMapper.readTree(res.getBody()).get("data");
    assertThat(preview.has("items")).isTrue();
    assertThat(preview.has("subtotalCents")).isTrue();
    assertThat(preview.has("shippingCents")).isTrue();
    assertThat(preview.has("totalCents")).isTrue();
    assertThat(preview.has("totalKhr")).isTrue();
    assertThat(preview.get("shippingMethod").get("code").asText()).isEqualTo("STANDARD");
    assertThat(preview.get("address").get("province").asText()).isEqualTo("Phnom Penh");
  }

  @Test
  @DisplayName("Fix5: preview availablePaymentMethods lists only enabled methods (KHQR, COD)")
  void testPreviewListsOnlyEnabledPaymentMethods() throws Exception {
    String token = loginCustomer("customer1@khmermart.local", "Customer@123");
    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth(token);
    restTemplate.exchange(
        "/api/cart/items",
        HttpMethod.POST,
        new HttpEntity<>(new CartItemRequest(1001L, null, 1), headers),
        String.class);

    CheckoutRequest req = new CheckoutRequest(1L, "STANDARD", PaymentMethod.COD, null);
    ResponseEntity<String> res =
        restTemplate.exchange(
            "/api/checkout/preview", HttpMethod.POST, new HttpEntity<>(req, headers), String.class);

    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
    JsonNode methods =
        objectMapper.readTree(res.getBody()).get("data").get("availablePaymentMethods");
    assertThat(methods).hasSize(2);
    assertThat(methods.get(0).asText()).isEqualTo("KHQR");
    assertThat(methods.get(1).asText()).isEqualTo("COD");
  }

  @Test
  @DisplayName("4. Checkout: missing Idempotency-Key returns 400 BAD_REQUEST / VALIDATION_ERROR")
  void testCheckoutMissingIdempotencyKey() throws Exception {
    String token = loginCustomer("customer1@khmermart.local", "Customer@123");
    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth(token);

    CheckoutRequest req = new CheckoutRequest(1L, "STANDARD", PaymentMethod.COD, null);
    // Notice: no Idempotency-Key header
    ResponseEntity<String> res =
        restTemplate.exchange(
            "/api/checkout", HttpMethod.POST, new HttpEntity<>(req, headers), String.class);

    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    JsonNode json = objectMapper.readTree(res.getBody());
    assertThat(json.get("success").asBoolean()).isFalse();
    assertThat(json.get("error").get("code").asText()).isEqualTo("VALIDATION_ERROR");
  }

  @Test
  @DisplayName(
      "5. Idempotent checkout: same Idempotency-Key returns exact same order without duplication")
  void testIdempotentCheckout() throws Exception {
    String token = loginCustomer("customer1@khmermart.local", "Customer@123");
    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth(token);

    // Add item to cart
    restTemplate.exchange(
        "/api/cart/items",
        HttpMethod.POST,
        new HttpEntity<>(new CartItemRequest(1001L, null, 1), headers),
        String.class);

    String idemKey = UUID.randomUUID().toString();
    headers.add("Idempotency-Key", idemKey);

    CheckoutRequest req =
        new CheckoutRequest(1L, "STANDARD", PaymentMethod.COD, "Handle with care");

    // First call -> Order created
    ResponseEntity<String> firstCall =
        restTemplate.exchange(
            "/api/checkout", HttpMethod.POST, new HttpEntity<>(req, headers), String.class);
    assertThat(firstCall.getStatusCode()).isEqualTo(HttpStatus.OK);
    JsonNode firstData = objectMapper.readTree(firstCall.getBody()).get("data");
    String orderNo = firstData.get("order").get("orderNumber").asText();
    String nextAction = firstData.get("next").asText();
    assertThat(nextAction).isEqualTo("ORDER_SUCCESS");
    assertThat(firstData.get("order").get("status").asText()).isEqualTo("CONFIRMED");
    JsonNode timeline0 = firstData.get("order").get("timeline").get(0);
    assertThat(timeline0.has("at")).isTrue();
    assertThat(timeline0.has("timestamp")).isFalse();
    assertThat(timeline0.has("status")).isTrue();
    assertThat(timeline0.has("note")).isTrue();

    // Second call with EXACT SAME Idempotency-Key -> returns identical cached order
    ResponseEntity<String> secondCall =
        restTemplate.exchange(
            "/api/checkout", HttpMethod.POST, new HttpEntity<>(req, headers), String.class);
    assertThat(secondCall.getStatusCode()).isEqualTo(HttpStatus.OK);
    JsonNode secondData = objectMapper.readTree(secondCall.getBody()).get("data");
    assertThat(secondData.get("order").get("orderNumber").asText()).isEqualTo(orderNo);
    assertThat(secondData.get("next").asText()).isEqualTo(nextAction);
  }

  @Test
  @DisplayName(
      "6. Price change detection: changing product price after adding to cart returns 409 PRICE_CHANGED")
  void testPriceChangeDetection() throws Exception {
    String token = loginCustomer("customer1@khmermart.local", "Customer@123");
    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth(token);

    // Add Product 1009 to cart
    restTemplate.exchange(
        "/api/cart/items",
        HttpMethod.POST,
        new HttpEntity<>(new CartItemRequest(1009L, null, 1), headers),
        String.class);

    // Modify product price in database directly
    Product product = productRepository.findById(1009L).orElseThrow();
    long originalPrice = product.getPriceCents();
    product.setPriceCents(originalPrice + 1000L); // Price increased by $10
    productRepository.save(product);

    try {
      headers.add("Idempotency-Key", UUID.randomUUID().toString());
      CheckoutRequest req = new CheckoutRequest(1L, "STANDARD", PaymentMethod.COD, null);
      ResponseEntity<String> res =
          restTemplate.exchange(
              "/api/checkout", HttpMethod.POST, new HttpEntity<>(req, headers), String.class);

      assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
      JsonNode json = objectMapper.readTree(res.getBody());
      assertThat(json.get("success").asBoolean()).isFalse();
      assertThat(json.get("error").get("code").asText()).isEqualTo("PRICE_CHANGED");
    } finally {
      // Revert product price
      product.setPriceCents(originalPrice);
      productRepository.save(product);
    }
  }
}
