package com.khmermart.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.khmermart.AbstractIntegrationTest;
import com.khmermart.model.enumerate.PaymentMethod;
import com.khmermart.model.request.AddressRequest;
import com.khmermart.model.request.AdminSettingsUpdateRequest;
import com.khmermart.model.request.AuthRequest;
import com.khmermart.model.request.CartItemRequest;
import com.khmermart.model.request.CheckoutRequest;
import com.khmermart.model.response.ShippingMethodDto;
import java.math.BigDecimal;
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

class AddressLocationShippingFeeIntegrationTest extends AbstractIntegrationTest {

  @Autowired private TestRestTemplate restTemplate;
  @Autowired private ObjectMapper objectMapper;

  private HttpHeaders login(String email, String password) throws Exception {
    ResponseEntity<String> res =
        restTemplate.postForEntity(
            "/api/auth/login", new AuthRequest(email, password), String.class);
    HttpHeaders h = new HttpHeaders();
    h.setBearerAuth(objectMapper.readTree(res.getBody()).get("data").get("accessToken").asText());
    return h;
  }

  private AddressRequest address(String postal, BigDecimal lat, BigDecimal lng) {
    return new AddressRequest(
        "Home",
        "Tester",
        "+85512444555",
        "#1 St 2",
        null,
        "Sen Sok",
        "Phnom Penh",
        postal,
        lat,
        lng,
        false);
  }

  private ResponseEntity<String> postAddress(HttpHeaders h, AddressRequest r) {
    return restTemplate.exchange(
        "/api/users/me/addresses", HttpMethod.POST, new HttpEntity<>(r, h), String.class);
  }

  @Test
  @DisplayName(
      "Address: lat/lng saved and returned; postal code optional; outside Cambodia rejected")
  void addressCoordinates() throws Exception {
    HttpHeaders h = login("customer2@khmermart.local", "Customer@123");

    ResponseEntity<String> ok =
        postAddress(h, address(null, new BigDecimal("11.556374"), new BigDecimal("104.928207")));
    assertThat(ok.getStatusCode()).isEqualTo(HttpStatus.OK);
    JsonNode data = objectMapper.readTree(ok.getBody()).get("data");
    assertThat(data.get("latitude").decimalValue()).isEqualByComparingTo("11.556374");
    assertThat(data.get("longitude").decimalValue()).isEqualByComparingTo("104.928207");
    assertThat(data.path("postalCode").isMissingNode() || data.path("postalCode").isNull())
        .isTrue();

    // no coordinates at all is fine (existing clients)
    assertThat(postAddress(h, address("12000", null, null)).getStatusCode())
        .isEqualTo(HttpStatus.OK);

    // outside Cambodia (Bangkok) and invalid range -> VALIDATION_ERROR
    for (BigDecimal[] bad :
        new BigDecimal[][] {
          {new BigDecimal("13.75"), new BigDecimal("100.50")},
          {new BigDecimal("95"), new BigDecimal("104.9")},
          {new BigDecimal("11.5"), new BigDecimal("190")}
        }) {
      ResponseEntity<String> r = postAddress(h, address(null, bad[0], bad[1]));
      assertThat(r.getStatusCode().is4xxClientError()).isTrue();
      assertThat(objectMapper.readTree(r.getBody()).get("error").get("code").asText())
          .isEqualTo("VALIDATION_ERROR");
    }

    // only one coordinate -> VALIDATION_ERROR
    ResponseEntity<String> half = postAddress(h, address(null, new BigDecimal("11.5"), null));
    assertThat(objectMapper.readTree(half.getBody()).get("error").get("code").asText())
        .isEqualTo("VALIDATION_ERROR");
  }

  @Test
  @DisplayName("Admin updates EXPRESS fee to 5 cents; saved, listed and used by checkout preview")
  void shippingFeeUpdateAppliesImmediately() throws Exception {
    HttpHeaders admin = login("admin@khmermart.local", "Admin@12345");
    HttpHeaders customer = login("customer1@khmermart.local", "Customer@123");
    restTemplate.exchange(
        "/api/cart/items",
        HttpMethod.POST,
        new HttpEntity<>(new CartItemRequest(1001L, null, 1), customer),
        String.class);

    ShippingMethodDto before = express();
    try {
      // negative fee rejected
      assertThat(putExpress(admin, -1L, null).getStatusCode().is4xxClientError()).isTrue();
      assertThat(express().feeCents()).isEqualTo(before.feeCents());

      ResponseEntity<String> res = putExpress(admin, 5L, null);
      assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(express().feeCents()).isEqualTo(5L);
      assertThat(express().freeOverCents()).isNull();

      CheckoutRequest req = new CheckoutRequest(1L, "EXPRESS", PaymentMethod.COD, null);
      ResponseEntity<String> prev =
          restTemplate.exchange(
              "/api/checkout/preview",
              HttpMethod.POST,
              new HttpEntity<>(req, customer),
              String.class);
      assertThat(prev.getStatusCode()).isEqualTo(HttpStatus.OK);
      JsonNode p = objectMapper.readTree(prev.getBody()).get("data");
      assertThat(p.get("shippingCents").asLong()).isEqualTo(5L);
    } finally {
      putExpress(admin, before.feeCents(), before.freeOverCents());
    }
  }

  private ShippingMethodDto express() throws Exception {
    JsonNode arr =
        objectMapper
            .readTree(restTemplate.getForEntity("/api/shipping/methods", String.class).getBody())
            .get("data");
    for (JsonNode n : arr) {
      if ("EXPRESS".equals(n.get("code").asText())) {
        return new ShippingMethodDto(
            "EXPRESS",
            n.get("name").asText(),
            n.get("nameKm").asText(),
            n.get("feeCents").asLong(),
            n.path("freeOverCents").isNumber() ? n.get("freeOverCents").asLong() : null,
            n.get("etaDaysMin").asInt(),
            n.get("etaDaysMax").asInt(),
            n.get("phnomPenhOnly").asBoolean());
      }
    }
    throw new IllegalStateException("EXPRESS missing");
  }

  private ResponseEntity<String> putExpress(HttpHeaders admin, Long fee, Long freeOver) {
    ShippingMethodDto dto = new ShippingMethodDto("EXPRESS", "x", "x", fee, freeOver, 1, 2, false);
    return restTemplate.exchange(
        "/api/admin/settings",
        HttpMethod.PUT,
        new HttpEntity<>(new AdminSettingsUpdateRequest(null, null, null, List.of(dto)), admin),
        String.class);
  }
}
