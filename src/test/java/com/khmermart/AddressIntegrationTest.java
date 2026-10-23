package com.khmermart;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.khmermart.modules.address.dto.CreateAddressRequest;
import com.khmermart.modules.address.dto.UpdateAddressRequest;
import com.khmermart.modules.auth.dto.RegisterRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class AddressIntegrationTest extends AbstractIntegrationTest {

  @Autowired private TestRestTemplate restTemplate;

  @Autowired private ObjectMapper objectMapper;

  @Test
  @DisplayName(
      "Address CRUD: first address becomes default; second default address unsets first; delete default promotes next")
  void testAddressCrudAndDefaultAddressRule() throws Exception {
    String email = "addr_" + System.currentTimeMillis() + "@khmermart.local";
    RegisterRequest reg =
        new RegisterRequest(email, "Pass123456", "Address Tester", "+85512444555");
    ResponseEntity<String> regResp =
        restTemplate.postForEntity("/api/auth/register", reg, String.class);
    JsonNode regJson = objectMapper.readTree(regResp.getBody());
    String token = regJson.get("data").get("accessToken").asText();

    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth(token);

    // 1. Add Address 1 with isDefault = false -> Since it's the first, service forces isDefault =
    // true
    CreateAddressRequest addr1Req =
        new CreateAddressRequest(
            "Home",
            "Sopheap",
            "+85512444555",
            "#123 St 2004",
            "Apt 4B",
            "Sen Sok",
            "Phnom Penh",
            "120801",
            false);
    ResponseEntity<String> addr1Resp =
        restTemplate.exchange(
            "/api/users/me/addresses",
            HttpMethod.POST,
            new HttpEntity<>(addr1Req, headers),
            String.class);
    assertThat(addr1Resp.getStatusCode()).isEqualTo(HttpStatus.OK);
    JsonNode addr1Json = objectMapper.readTree(addr1Resp.getBody());
    long addr1Id = addr1Json.get("data").get("id").asLong();
    assertThat(addr1Json.get("data").get("isDefault").asBoolean()).isTrue();

    // 2. Add Address 2 with isDefault = true -> Must unset Address 1 default
    CreateAddressRequest addr2Req =
        new CreateAddressRequest(
            "Office",
            "Sopheap Office",
            "+85512999000",
            "#456 Russian Blvd",
            null,
            "Tuol Kouk",
            "Phnom Penh",
            "120101",
            true);
    ResponseEntity<String> addr2Resp =
        restTemplate.exchange(
            "/api/users/me/addresses",
            HttpMethod.POST,
            new HttpEntity<>(addr2Req, headers),
            String.class);
    assertThat(addr2Resp.getStatusCode()).isEqualTo(HttpStatus.OK);
    JsonNode addr2Json = objectMapper.readTree(addr2Resp.getBody());
    long addr2Id = addr2Json.get("data").get("id").asLong();
    assertThat(addr2Json.get("data").get("isDefault").asBoolean()).isTrue();

    // 3. GET /api/users/me/addresses -> verify Address 1 is now false, Address 2 is true
    ResponseEntity<String> listResp =
        restTemplate.exchange(
            "/api/users/me/addresses", HttpMethod.GET, new HttpEntity<>(headers), String.class);
    assertThat(listResp.getStatusCode()).isEqualTo(HttpStatus.OK);
    JsonNode listJson = objectMapper.readTree(listResp.getBody());
    JsonNode items = listJson.get("data");
    assertThat(items.size()).isEqualTo(2);

    for (JsonNode item : items) {
      if (item.get("id").asLong() == addr1Id) {
        assertThat(item.get("isDefault").asBoolean()).isFalse();
      } else if (item.get("id").asLong() == addr2Id) {
        assertThat(item.get("isDefault").asBoolean()).isTrue();
      }
    }

    // 4. PATCH Address 1 to isDefault = true -> Address 1 is true, Address 2 becomes false
    UpdateAddressRequest patchReq =
        new UpdateAddressRequest(null, null, null, null, null, null, null, null, true);
    ResponseEntity<String> patchResp =
        restTemplate.exchange(
            "/api/users/me/addresses/" + addr1Id,
            HttpMethod.PATCH,
            new HttpEntity<>(patchReq, headers),
            String.class);
    assertThat(patchResp.getStatusCode()).isEqualTo(HttpStatus.OK);
    JsonNode patchJson = objectMapper.readTree(patchResp.getBody());
    assertThat(patchJson.get("data").get("isDefault").asBoolean()).isTrue();

    // Verify Address 2 is now false
    ResponseEntity<String> listResp2 =
        restTemplate.exchange(
            "/api/users/me/addresses", HttpMethod.GET, new HttpEntity<>(headers), String.class);
    JsonNode items2 = objectMapper.readTree(listResp2.getBody()).get("data");
    for (JsonNode item : items2) {
      if (item.get("id").asLong() == addr1Id) {
        assertThat(item.get("isDefault").asBoolean()).isTrue();
      } else if (item.get("id").asLong() == addr2Id) {
        assertThat(item.get("isDefault").asBoolean()).isFalse();
      }
    }

    // 5. DELETE default Address 1 -> Address 2 must be automatically promoted to default
    ResponseEntity<String> delResp =
        restTemplate.exchange(
            "/api/users/me/addresses/" + addr1Id,
            HttpMethod.DELETE,
            new HttpEntity<>(headers),
            String.class);
    assertThat(delResp.getStatusCode()).isEqualTo(HttpStatus.OK);

    ResponseEntity<String> listResp3 =
        restTemplate.exchange(
            "/api/users/me/addresses", HttpMethod.GET, new HttpEntity<>(headers), String.class);
    JsonNode items3 = objectMapper.readTree(listResp3.getBody()).get("data");
    assertThat(items3.size()).isEqualTo(1);
    assertThat(items3.get(0).get("id").asLong()).isEqualTo(addr2Id);
    assertThat(items3.get(0).get("isDefault").asBoolean()).isTrue();
  }
}
