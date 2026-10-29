package com.khmermart.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.khmermart.AbstractIntegrationTest;
import com.khmermart.model.request.ApplyCouponRequest;
import com.khmermart.model.request.AuthRequest;
import com.khmermart.model.request.CartItemRequest;
import com.khmermart.seed.SeedCatalog;
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

    // Add item 1: Product 1001 (Takeo Hol Silk Sampot, $149.00 = 14900c) qty 1
    CartItemRequest item1 = new CartItemRequest(SeedCatalog.HERO_ID, null, 1);
    ResponseEntity<String> addRes1 =
        restTemplate.exchange(
            "/api/cart/items", HttpMethod.POST, new HttpEntity<>(item1, headers), String.class);
    assertThat(addRes1.getStatusCode()).isEqualTo(HttpStatus.OK);
    JsonNode cart1 = objectMapper.readTree(addRes1.getBody()).get("data");
    assertThat(cart1.get("items").size()).isEqualTo(1);
    assertThat(cart1.get("subtotalCents").asLong()).isEqualTo(SeedCatalog.HERO_STANDARD_CENTS);

    // Add item 2: Product 1005 (Phamuong Silk Fabric 2 m, $65.00 = 6500c) qty 2
    CartItemRequest item2 = new CartItemRequest(1005L, null, 2);
    ResponseEntity<String> addRes2 =
        restTemplate.exchange(
            "/api/cart/items", HttpMethod.POST, new HttpEntity<>(item2, headers), String.class);
    assertThat(addRes2.getStatusCode()).isEqualTo(HttpStatus.OK);
    JsonNode cart2 = objectMapper.readTree(addRes2.getBody()).get("data");
    assertThat(cart2.get("items").size()).isEqualTo(2);
    assertThat(cart2.get("itemCount").asInt()).isEqualTo(3);
    assertThat(cart2.get("subtotalCents").asLong())
        .isEqualTo(SeedCatalog.HERO_STANDARD_CENTS + (SeedCatalog.product(1005L).priceCents() * 2));
  }

  @Test
  @DisplayName(
      "2. Stock enforcement: adding quantity > available returns 409 OUT_OF_STOCK with details.available")
  void testOutOfStockValidation() throws Exception {
    HttpHeaders headers = new HttpHeaders();
    headers.add(HttpHeaders.COOKIE, "km_guest=stock-test-token");

    // Product 1001 has 4 variants x 50 = 200 available. Requesting one more should fail with 409
    CartItemRequest req =
        new CartItemRequest(SeedCatalog.HERO_ID, null, 4 * SeedCatalog.HERO_VARIANT_STOCK + 1);
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

    // Initially add cheap item: Product 1038 (Khmer-English Dictionary, $18.00 = 1800c, below SAVE5
    // min order of $35 = 3500c)
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

    // B. SAVE5 with subtotal 1800c < 3500c -> 422 COUPON_MIN_ORDER
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

    // Add 2 more units of Product 1038 ($18.00 * 3 = $54.00 = 5400c, >= $35 min order)
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

    // Add Product 1005 (Phamuong Silk Fabric) qty 2 to guest cart
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
      "5. Per-variant flash sale: Premium variant -> no flash discount; Standard variant -> flash price; mixed -> only Standard discounted")
  void testPerVariantFlashSalePricingInCart() throws Exception {
    long standard = SeedCatalog.HERO_STANDARD_CENTS;
    long premium = SeedCatalog.HERO_PREMIUM_CENTS;
    long flashDiscount = standard - SeedCatalog.HERO_FLASH_CENTS;

    // A. Premium variant (Ruby Red) in cart -> no flash discount
    HttpHeaders premiumHeaders = new HttpHeaders();
    premiumHeaders.add(HttpHeaders.COOKIE, "km_guest=flash-sale-test-premium");

    CartItemRequest reqPremium =
        new CartItemRequest(SeedCatalog.HERO_ID, SeedCatalog.HERO_RED_PREMIUM, 1);
    ResponseEntity<String> resPremium =
        restTemplate.exchange(
            "/api/cart/items",
            HttpMethod.POST,
            new HttpEntity<>(reqPremium, premiumHeaders),
            String.class);
    assertThat(resPremium.getStatusCode()).isEqualTo(HttpStatus.OK);
    JsonNode cartPremium = objectMapper.readTree(resPremium.getBody()).get("data");

    assertThat(cartPremium.get("subtotalCents").asLong()).isEqualTo(premium);
    assertThat(cartPremium.get("discountCents").asLong()).isEqualTo(0L);
    assertThat(cartPremium.get("totalCents").asLong()).isEqualTo(premium);
    assertThat(cartPremium.get("promotionDiscounts").size()).isEqualTo(0);

    // B. Standard variant (Ruby Red) in cart -> flash discount applied
    HttpHeaders standardHeaders = new HttpHeaders();
    standardHeaders.add(HttpHeaders.COOKIE, "km_guest=flash-sale-test-standard");

    CartItemRequest reqStandard =
        new CartItemRequest(SeedCatalog.HERO_ID, SeedCatalog.HERO_RED_STANDARD, 1);
    ResponseEntity<String> resStandard =
        restTemplate.exchange(
            "/api/cart/items",
            HttpMethod.POST,
            new HttpEntity<>(reqStandard, standardHeaders),
            String.class);
    assertThat(resStandard.getStatusCode()).isEqualTo(HttpStatus.OK);
    JsonNode cartStandard = objectMapper.readTree(resStandard.getBody()).get("data");

    assertThat(cartStandard.get("subtotalCents").asLong()).isEqualTo(standard);
    assertThat(cartStandard.get("discountCents").asLong()).isEqualTo(flashDiscount);
    assertThat(cartStandard.get("totalCents").asLong()).isEqualTo(SeedCatalog.HERO_FLASH_CENTS);
    assertThat(cartStandard.get("promotionDiscounts").size()).isEqualTo(1);
    assertThat(cartStandard.get("promotionDiscounts").get(0).get("label").asText())
        .isEqualTo("Flash Sale Discount");

    // C. Mixed cart (Standard + Premium) -> only Standard discounted
    HttpHeaders mixedHeaders = new HttpHeaders();
    mixedHeaders.add(HttpHeaders.COOKIE, "km_guest=flash-sale-test-mixed");

    restTemplate.exchange(
        "/api/cart/items",
        HttpMethod.POST,
        new HttpEntity<>(reqStandard, mixedHeaders),
        String.class);
    ResponseEntity<String> resMixed =
        restTemplate.exchange(
            "/api/cart/items",
            HttpMethod.POST,
            new HttpEntity<>(reqPremium, mixedHeaders),
            String.class);
    assertThat(resMixed.getStatusCode()).isEqualTo(HttpStatus.OK);
    JsonNode cartMixed = objectMapper.readTree(resMixed.getBody()).get("data");

    assertThat(cartMixed.get("subtotalCents").asLong()).isEqualTo(standard + premium);
    assertThat(cartMixed.get("discountCents").asLong()).isEqualTo(flashDiscount); // only Standard
    assertThat(cartMixed.get("totalCents").asLong()).isEqualTo(standard + premium - flashDiscount);
    assertThat(cartMixed.get("promotionDiscounts").size()).isEqualTo(1);
    assertThat(cartMixed.get("promotionDiscounts").get(0).get("amountCents").asLong())
        .isEqualTo(flashDiscount);
  }

  @Test
  @DisplayName(
      "6. GET /api/products/1001 -> Standard variants carry the flash salePriceCents, Premium variants have none")
  void testHeroVariantSalePrices() throws Exception {
    ResponseEntity<String> response =
        restTemplate.getForEntity("/api/products/" + SeedCatalog.HERO_ID, String.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

    JsonNode json = objectMapper.readTree(response.getBody());
    assertThat(json.get("success").asBoolean()).isTrue();
    JsonNode p = json.get("data");

    JsonNode variants = p.get("variants");
    assertThat(variants.size()).isEqualTo(4);

    // Variants 0 and 2 (Standard) on flash sale
    assertThat(variants.get(0).get("salePriceCents").asLong())
        .isEqualTo(SeedCatalog.HERO_FLASH_CENTS);
    assertThat(variants.get(2).get("salePriceCents").asLong())
        .isEqualTo(SeedCatalog.HERO_FLASH_CENTS);

    // Variants 1 and 3 (Premium) NOT on flash sale (salePriceCents absent/omitted)
    assertThat(variants.get(1).has("salePriceCents")).isFalse();
    assertThat(variants.get(3).has("salePriceCents")).isFalse();

    // Product-level salePriceCents is the lowest active variant sale price
    assertThat(p.get("salePriceCents").asLong()).isEqualTo(SeedCatalog.HERO_FLASH_CENTS);
  }

  @Test
  @DisplayName(
      "7. Product-wide flash sale: Kampot Black Pepper and Dried Mango carry their flash prices without variants")
  void testProductWideFlashPrices() throws Exception {
    for (long[] flash :
        new long[][] {
          {SeedCatalog.PEPPER_ID, SeedCatalog.PEPPER_FLASH_CENTS},
          {SeedCatalog.MANGO_ID, SeedCatalog.MANGO_FLASH_CENTS}
        }) {
      ResponseEntity<String> response =
          restTemplate.getForEntity("/api/products/" + flash[0], String.class);
      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
      JsonNode p = objectMapper.readTree(response.getBody()).get("data");
      assertThat(p.get("priceCents").asLong())
          .isEqualTo(SeedCatalog.product(flash[0]).priceCents());
      assertThat(p.get("salePriceCents").asLong()).isEqualTo(flash[1]);
    }
  }
}
