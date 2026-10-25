package com.khmermart.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.khmermart.AbstractIntegrationTest;
import com.khmermart.model.request.ApplyCouponRequest;
import com.khmermart.model.request.AuthRequest;
import com.khmermart.model.request.CartItemRequest;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class CartIntegrationTest extends AbstractIntegrationTest {

  @Autowired private TestRestTemplate restTemplate;
  @Autowired private ObjectMapper objectMapper;

  @Test
  @DisplayName(
      "1. Guest cart: GET /api/cart sets km_guest cookie; adding items updates cart totals")
  void testGuestCartLifecycle() throws Exception {
    // 1. GET /api/cart with no cookie -> Set-Cookie km_guest
    ResponseEntity<String> getRes = restTemplate.getForEntity("/api/cart", String.class);
    assertThat(getRes.getStatusCode()).isEqualTo(HttpStatus.OK);

    List<String> cookies = getRes.getHeaders().get(HttpHeaders.SET_COOKIE);
    assertThat(cookies).isNotNull();
    String guestCookie =
        cookies.stream().filter(c -> c.startsWith("km_guest=")).findFirst().orElse(null);
    assertThat(guestCookie).isNotNull();
    assertThat(guestCookie).contains("HttpOnly");
    assertThat(guestCookie).contains("Path=/api/cart");

    String rawCookie = guestCookie.split(";")[0]; // e.g. km_guest=xxxx-yyyy

    // 2. Add 2 items to guest cart
    HttpHeaders headers = new HttpHeaders();
    headers.add(HttpHeaders.COOKIE, rawCookie);

    // Add item 1: Product 1001 (Laptop Pro 15, $999.00 = 99900c) qty 1
    CartItemRequest item1 = new CartItemRequest(1001L, null, 1);
    ResponseEntity<String> addRes1 =
        restTemplate.exchange(
            "/api/cart/items", HttpMethod.POST, new HttpEntity<>(item1, headers), String.class);
    assertThat(addRes1.getStatusCode()).isEqualTo(HttpStatus.OK);
    JsonNode cart1 = objectMapper.readTree(addRes1.getBody()).get("data");
    assertThat(cart1.get("items").size()).isEqualTo(1);
    assertThat(cart1.get("subtotalCents").asLong()).isEqualTo(99900L);

    // Add item 2: Product 1005 (Dell Vostro 14, $599.00 = 59900c) qty 2
    CartItemRequest item2 = new CartItemRequest(1005L, null, 2);
    ResponseEntity<String> addRes2 =
        restTemplate.exchange(
            "/api/cart/items", HttpMethod.POST, new HttpEntity<>(item2, headers), String.class);
    assertThat(addRes2.getStatusCode()).isEqualTo(HttpStatus.OK);
    JsonNode cart2 = objectMapper.readTree(addRes2.getBody()).get("data");
    assertThat(cart2.get("items").size()).isEqualTo(2);
    assertThat(cart2.get("itemCount").asInt()).isEqualTo(3);
    assertThat(cart2.get("subtotalCents").asLong()).isEqualTo(99900L + (59900L * 2));
  }

  @Test
  @DisplayName(
      "2. Stock enforcement: adding quantity > available returns 409 OUT_OF_STOCK with details.available")
  void testOutOfStockValidation() throws Exception {
    HttpHeaders headers = new HttpHeaders();
    headers.add(HttpHeaders.COOKIE, "km_guest=stock-test-token");

    // Product 1001 has available = 50. Requesting 100 should fail with 409 OUT_OF_STOCK
    CartItemRequest req = new CartItemRequest(1001L, null, 100);
    ResponseEntity<String> res =
        restTemplate.exchange(
            "/api/cart/items", HttpMethod.POST, new HttpEntity<>(req, headers), String.class);

    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    JsonNode json = objectMapper.readTree(res.getBody());
    assertThat(json.get("success").asBoolean()).isFalse();
    assertThat(json.get("error").get("code").asText()).isEqualTo("OUT_OF_STOCK");
    assertThat(json.get("error").get("details").has("available")).isTrue();
  }

  @Test
  @DisplayName(
      "3. Coupon validation: INVALID, MIN_ORDER, EXPIRED and successful application / removal")
  void testCouponLifecycle() throws Exception {
    HttpHeaders headers = new HttpHeaders();
    headers.add(HttpHeaders.COOKIE, "km_guest=coupon-test-token");

    // Initially add cheap item: Product 1038 (Atomic Habits book, $14.00 = 1400c, below SAVE5 min
    // order of $35 = 3500c)
    CartItemRequest cheapItem = new CartItemRequest(1038L, null, 1);
    restTemplate.exchange(
        "/api/cart/items", HttpMethod.POST, new HttpEntity<>(cheapItem, headers), String.class);

    // A. Invalid coupon -> 422 COUPON_INVALID
    ResponseEntity<String> invRes =
        restTemplate.exchange(
            "/api/cart/coupon",
            HttpMethod.POST,
            new HttpEntity<>(new ApplyCouponRequest("INVALID_CODE"), headers),
            String.class);
    assertThat(invRes.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    assertThat(objectMapper.readTree(invRes.getBody()).get("error").get("code").asText())
        .isEqualTo("COUPON_INVALID");

    // B. SAVE5 with subtotal 1400c < 3500c -> 422 COUPON_MIN_ORDER
    ResponseEntity<String> minOrderRes =
        restTemplate.exchange(
            "/api/cart/coupon",
            HttpMethod.POST,
            new HttpEntity<>(new ApplyCouponRequest("SAVE5"), headers),
            String.class);
    assertThat(minOrderRes.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    assertThat(objectMapper.readTree(minOrderRes.getBody()).get("error").get("code").asText())
        .isEqualTo("COUPON_MIN_ORDER");

    // C. EXPIRED coupon -> 422 COUPON_EXPIRED
    ResponseEntity<String> expRes =
        restTemplate.exchange(
            "/api/cart/coupon",
            HttpMethod.POST,
            new HttpEntity<>(new ApplyCouponRequest("EXPIRED"), headers),
            String.class);
    assertThat(expRes.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    assertThat(objectMapper.readTree(expRes.getBody()).get("error").get("code").asText())
        .isEqualTo("COUPON_EXPIRED");

    // Add 2 more units of Product 1038 ($14.00 * 3 = $42.00 = 4200c, >= $35 min order and < $50
    // seasonal promo threshold)
    CartItemRequest moreItems = new CartItemRequest(1038L, null, 2);
    restTemplate.exchange(
        "/api/cart/items", HttpMethod.POST, new HttpEntity<>(moreItems, headers), String.class);

    // D. Apply SAVE5 successfully
    ResponseEntity<String> save5Res =
        restTemplate.exchange(
            "/api/cart/coupon",
            HttpMethod.POST,
            new HttpEntity<>(new ApplyCouponRequest("SAVE5"), headers),
            String.class);
    assertThat(save5Res.getStatusCode()).isEqualTo(HttpStatus.OK);
    JsonNode appliedCart = objectMapper.readTree(save5Res.getBody()).get("data");
    assertThat(appliedCart.get("discountCents").asLong()).isEqualTo(500L);
    assertThat(appliedCart.get("coupon").get("code").asText()).isEqualTo("SAVE5");

    // E. Remove coupon
    ResponseEntity<String> remRes =
        restTemplate.exchange(
            "/api/cart/coupon", HttpMethod.DELETE, new HttpEntity<>(headers), String.class);
    assertThat(remRes.getStatusCode()).isEqualTo(HttpStatus.OK);
    JsonNode removedCart = objectMapper.readTree(remRes.getBody()).get("data");
    assertThat(removedCart.get("discountCents").asLong()).isEqualTo(0L);
    assertThat(removedCart.hasNonNull("coupon")).isFalse();
  }

  @Test
  @DisplayName(
      "4. Cart merge: guest cart items merge into customer account on login and km_guest cookie is cleared")
  void testCartMergeOnLogin() throws Exception {
    String guestToken = "merge-guest-token";
    HttpHeaders guestHeaders = new HttpHeaders();
    guestHeaders.add(HttpHeaders.COOKIE, "km_guest=" + guestToken);

    // Add Product 1005 qty 2 to guest cart
    restTemplate.exchange(
        "/api/cart/items",
        HttpMethod.POST,
        new HttpEntity<>(new CartItemRequest(1005L, null, 2), guestHeaders),
        String.class);

    // Customer logs in
    AuthRequest loginReq = new AuthRequest("customer1@khmermart.local", "Customer@123");
    ResponseEntity<String> loginRes =
        restTemplate.postForEntity("/api/auth/login", loginReq, String.class);
    String token =
        objectMapper.readTree(loginRes.getBody()).get("data").get("accessToken").asText();

    HttpHeaders authHeaders = new HttpHeaders();
    authHeaders.setBearerAuth(token);
    authHeaders.add(HttpHeaders.COOKIE, "km_guest=" + guestToken);

    // Merge cart
    ResponseEntity<String> mergeRes =
        restTemplate.exchange(
            "/api/cart/merge", HttpMethod.POST, new HttpEntity<>(authHeaders), String.class);
    assertThat(mergeRes.getStatusCode()).isEqualTo(HttpStatus.OK);

    JsonNode mergedData = objectMapper.readTree(mergeRes.getBody()).get("data");
    assertThat(mergedData.get("items").size()).isGreaterThanOrEqualTo(1);

    // Verify km_guest cookie cleared with Max-Age=0
    List<String> setCookies = mergeRes.getHeaders().get(HttpHeaders.SET_COOKIE);
    assertThat(setCookies).isNotNull();
    String clearedCookie =
        setCookies.stream().filter(c -> c.startsWith("km_guest=")).findFirst().orElse(null);
    assertThat(clearedCookie).isNotNull();
    assertThat(clearedCookie).contains("Max-Age=0");
  }

  @Test
  @DisplayName(
      "5. Per-variant flash sale: variant 3 in cart -> total 129900 (no flash discount); variant 1 -> total 89900; mixed -> only variant 1 discounted")
  void testPerVariantFlashSalePricingInCart() throws Exception {
    // A. Variant 3 in cart -> no flash discount, total 129900
    HttpHeaders v3Headers = new HttpHeaders();
    v3Headers.add(HttpHeaders.COOKIE, "km_guest=flash-sale-test-v3");

    CartItemRequest reqV3 = new CartItemRequest(1001L, 3L, 1);
    ResponseEntity<String> resV3 =
        restTemplate.exchange(
            "/api/cart/items", HttpMethod.POST, new HttpEntity<>(reqV3, v3Headers), String.class);
    assertThat(resV3.getStatusCode()).isEqualTo(HttpStatus.OK);
    JsonNode cartV3 = objectMapper.readTree(resV3.getBody()).get("data");

    assertThat(cartV3.get("subtotalCents").asLong()).isEqualTo(129900L);
    assertThat(cartV3.get("discountCents").asLong()).isEqualTo(0L);
    assertThat(cartV3.get("totalCents").asLong()).isEqualTo(129900L);
    assertThat(cartV3.get("promotionDiscounts").size()).isEqualTo(0);

    // B. Variant 1 in cart -> flash discount applied, total 89900
    HttpHeaders v1Headers = new HttpHeaders();
    v1Headers.add(HttpHeaders.COOKIE, "km_guest=flash-sale-test-v1");

    CartItemRequest reqV1 = new CartItemRequest(1001L, 1L, 1);
    ResponseEntity<String> resV1 =
        restTemplate.exchange(
            "/api/cart/items", HttpMethod.POST, new HttpEntity<>(reqV1, v1Headers), String.class);
    assertThat(resV1.getStatusCode()).isEqualTo(HttpStatus.OK);
    JsonNode cartV1 = objectMapper.readTree(resV1.getBody()).get("data");

    assertThat(cartV1.get("subtotalCents").asLong()).isEqualTo(99900L);
    assertThat(cartV1.get("discountCents").asLong()).isEqualTo(10000L);
    assertThat(cartV1.get("totalCents").asLong()).isEqualTo(89900L);
    assertThat(cartV1.get("promotionDiscounts").size()).isEqualTo(1);
    assertThat(cartV1.get("promotionDiscounts").get(0).get("label").asText())
        .isEqualTo("Flash Sale Discount");

    // C. Mixed cart (Variant 1 + Variant 3) -> only Variant 1 discounted
    HttpHeaders mixedHeaders = new HttpHeaders();
    mixedHeaders.add(HttpHeaders.COOKIE, "km_guest=flash-sale-test-mixed");

    restTemplate.exchange(
        "/api/cart/items", HttpMethod.POST, new HttpEntity<>(reqV1, mixedHeaders), String.class);
    ResponseEntity<String> resMixed =
        restTemplate.exchange(
            "/api/cart/items",
            HttpMethod.POST,
            new HttpEntity<>(reqV3, mixedHeaders),
            String.class);
    assertThat(resMixed.getStatusCode()).isEqualTo(HttpStatus.OK);
    JsonNode cartMixed = objectMapper.readTree(resMixed.getBody()).get("data");

    assertThat(cartMixed.get("subtotalCents").asLong()).isEqualTo(99900L + 129900L); // 229800
    assertThat(cartMixed.get("discountCents").asLong()).isEqualTo(10000L); // only variant 1
    assertThat(cartMixed.get("totalCents").asLong()).isEqualTo(219800L);
    assertThat(cartMixed.get("promotionDiscounts").size()).isEqualTo(1);
    assertThat(cartMixed.get("promotionDiscounts").get(0).get("amountCents").asLong())
        .isEqualTo(10000L);
  }

  @Test
  @DisplayName(
      "6. GET /api/products/1001 -> variants[0..1].salePriceCents = 89900, variants[2..3] have no salePriceCents")
  void testProduct1001VariantSalePrices() throws Exception {
    ResponseEntity<String> response = restTemplate.getForEntity("/api/products/1001", String.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

    JsonNode json = objectMapper.readTree(response.getBody());
    assertThat(json.get("success").asBoolean()).isTrue();
    JsonNode p = json.get("data");

    JsonNode variants = p.get("variants");
    assertThat(variants.size()).isEqualTo(4);

    // Variants 0 and 1 on flash sale
    assertThat(variants.get(0).get("salePriceCents").asLong()).isEqualTo(89900L);
    assertThat(variants.get(1).get("salePriceCents").asLong()).isEqualTo(89900L);

    // Variants 2 and 3 NOT on flash sale (salePriceCents absent/omitted)
    assertThat(variants.get(2).has("salePriceCents")).isFalse();
    assertThat(variants.get(3).has("salePriceCents")).isFalse();

    // Product-level salePriceCents is the lowest active variant sale price
    assertThat(p.get("salePriceCents").asLong()).isEqualTo(89900L);
  }
}
