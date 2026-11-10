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

  private record UserAuth(String email, String token) {}

  private UserAuth registerUser(String prefix) throws Exception {
    String email = prefix + "_" + System.currentTimeMillis() + "@khmermart.local";
    RegisterRequest reg =
        new RegisterRequest(email, "InitialPass123", "Tester " + prefix, "+85512666777");
    String accessToken = registerVerifiedAndLogin(restTemplate, reg);
    return new UserAuth(email, accessToken);
  }

  @Test
  @DisplayName("GET /api/users/me returns authenticated user profile")
  void testGetCurrentUserProfile() throws Exception {
    UserAuth auth = registerUser("me_get");
    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth(auth.token());

    ResponseEntity<String> getMeResp =
        restTemplate.exchange(
            "/api/users/me", HttpMethod.GET, new HttpEntity<>(headers), String.class);
    assertThat(getMeResp.getStatusCode()).isEqualTo(HttpStatus.OK);
    JsonNode getMeJson = objectMapper.readTree(getMeResp.getBody());
    assertThat(getMeJson.get("success").asBoolean()).isTrue();
    assertThat(getMeJson.get("data").get("fullName").asText()).isEqualTo("Tester me_get");
    assertThat(getMeJson.get("data").get("emailVerified").asBoolean()).isTrue();
  }

  @Test
  @DisplayName("PATCH /api/users/me updates profile name and phone")
  void testUpdateCurrentUserProfile() throws Exception {
    UserAuth auth = registerUser("me_patch");
    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth(auth.token());

    AppUserUpdateRequest updateReq = new AppUserUpdateRequest("AppUser Updated", "+85598222333");
    ResponseEntity<String> patchResp =
        restTemplate.exchange(
            "/api/users/me", HttpMethod.PATCH, new HttpEntity<>(updateReq, headers), String.class);
    assertThat(patchResp.getStatusCode()).isEqualTo(HttpStatus.OK);
    JsonNode patchJson = objectMapper.readTree(patchResp.getBody());
    assertThat(patchJson.get("success").asBoolean()).isTrue();
    assertThat(patchJson.get("data").get("fullName").asText()).isEqualTo("AppUser Updated");
    assertThat(patchJson.get("data").get("phone").asText()).isEqualTo("+85598222333");
  }

  @Test
  @DisplayName(
      "PATCH /api/users/me/password with wrong current password returns 400 VALIDATION_ERROR")
  void testChangePasswordWrongCurrentPasswordReturns400ValidationError() throws Exception {
    UserAuth auth = registerUser("pwd_wrong");
    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth(auth.token());

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
  }

  @Test
  @DisplayName(
      "PATCH /api/users/me/password with correct current password succeeds and invalidates old password")
  void testChangePasswordSuccess() throws Exception {
    UserAuth auth = registerUser("pwd_ok");
    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth(auth.token());

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

    // Verify old password fails
    AuthRequest oldLogin = new AuthRequest(auth.email(), "InitialPass123");
    ResponseEntity<String> oldLoginResp =
        restTemplate.postForEntity("/api/auth/login", oldLogin, String.class);
    assertThat(oldLoginResp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

    // Verify login with new password works
    AuthRequest newLogin = new AuthRequest(auth.email(), "NewValidPass456");
    ResponseEntity<String> loginResp =
        restTemplate.postForEntity("/api/auth/login", newLogin, String.class);
    assertThat(loginResp.getStatusCode()).isEqualTo(HttpStatus.OK);
  }
}
