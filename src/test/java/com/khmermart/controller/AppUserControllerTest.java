package com.khmermart.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.khmermart.AbstractIntegrationTest;
import com.khmermart.model.request.AppUserUpdateRequest;
import com.khmermart.model.request.AuthRequest;
import com.khmermart.model.request.PasswordRequest;
import com.khmermart.model.request.RegisterRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class AppUserControllerTest extends AbstractIntegrationTest {

  @Autowired private TestRestTemplate restTemplate;

  @Autowired private ObjectMapper objectMapper;

  @Test
  @DisplayName(
      "AppUser endpoints: GET /me, PATCH /me, PATCH /me/password (wrong current -> 400 details.currentPassword, correct -> 200 data:{})")
  void testAppUserEndpoints() throws Exception {
    String email = "appuser_" + System.currentTimeMillis() + "@khmermart.local";
    RegisterRequest reg =
        new RegisterRequest(email, "InitialPass123", "AppUser Tester", "+85512666777");

    ResponseEntity<String> regResp =
        restTemplate.postForEntity("/api/auth/register", reg, String.class);
    JsonNode regJson = objectMapper.readTree(regResp.getBody());
    String accessToken = regJson.get("data").get("accessToken").asText();

    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth(accessToken);

    // 1. GET /api/users/me
    ResponseEntity<String> getMeResp =
        restTemplate.exchange(
            "/api/users/me", HttpMethod.GET, new HttpEntity<>(headers), String.class);
    assertThat(getMeResp.getStatusCode()).isEqualTo(HttpStatus.OK);
    JsonNode getMeJson = objectMapper.readTree(getMeResp.getBody());
    assertThat(getMeJson.get("data").get("fullName").asText()).isEqualTo("AppUser Tester");
    assertThat(getMeJson.get("data").get("emailVerified").asBoolean()).isFalse();

    // 2. PATCH /api/users/me
    AppUserUpdateRequest updateReq = new AppUserUpdateRequest("AppUser Updated", "+85598222333");
    ResponseEntity<String> patchResp =
        restTemplate.exchange(
            "/api/users/me", HttpMethod.PATCH, new HttpEntity<>(updateReq, headers), String.class);
    assertThat(patchResp.getStatusCode()).isEqualTo(HttpStatus.OK);
    JsonNode patchJson = objectMapper.readTree(patchResp.getBody());
    assertThat(patchJson.get("data").get("fullName").asText()).isEqualTo("AppUser Updated");
    assertThat(patchJson.get("data").get("phone").asText()).isEqualTo("+85598222333");

    // 3. PATCH /api/users/me/password with WRONG currentPassword -> 400 with
    // details.currentPassword
    PasswordRequest wrongReq = new PasswordRequest("WrongCurrentPass", "NewValidPass456");
    ResponseEntity<String> wrongResp =
        restTemplate.exchange(
            "/api/users/me/password",
            HttpMethod.PATCH,
            new HttpEntity<>(wrongReq, headers),
            String.class);
    assertThat(wrongResp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    JsonNode wrongJson = objectMapper.readTree(wrongResp.getBody());
    assertThat(wrongJson.get("error").get("code").asText()).isEqualTo("VALIDATION_ERROR");
    assertThat(wrongJson.get("error").get("details").has("currentPassword")).isTrue();

    // 4. PATCH /api/users/me/password with CORRECT currentPassword -> 200 with data:{}
    PasswordRequest correctReq = new PasswordRequest("InitialPass123", "NewValidPass456");
    ResponseEntity<String> correctResp =
        restTemplate.exchange(
            "/api/users/me/password",
            HttpMethod.PATCH,
            new HttpEntity<>(correctReq, headers),
            String.class);
    assertThat(correctResp.getStatusCode()).isEqualTo(HttpStatus.OK);
    JsonNode correctJson = objectMapper.readTree(correctResp.getBody());
    assertThat(correctJson.get("success").asBoolean()).isTrue();
    assertThat(correctJson.has("data")).isTrue();
    assertThat(correctJson.get("data").isObject()).isTrue();

    // 5. Verify login with new password works
    AuthRequest newLogin = new AuthRequest(email, "NewValidPass456");
    ResponseEntity<String> loginResp =
        restTemplate.postForEntity("/api/auth/login", newLogin, String.class);
    assertThat(loginResp.getStatusCode()).isEqualTo(HttpStatus.OK);
  }
}
