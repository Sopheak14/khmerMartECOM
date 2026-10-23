package com.khmermart.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.khmermart.AbstractIntegrationTest;
import com.khmermart.model.request.AppUserUpdateRequest;
import com.khmermart.model.request.AuthRequest;
import com.khmermart.model.request.PasswordRequest;
import com.khmermart.model.request.RegisterRequest;
import com.khmermart.repository.EmailVerificationTokenRepository;
import com.khmermart.repository.PasswordResetTokenRepository;
import com.khmermart.security.JwtService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class AuthIntegrationTest extends AbstractIntegrationTest {

  @Autowired private TestRestTemplate restTemplate;

  @Autowired private ObjectMapper objectMapper;

  @Autowired private JwtService jwtService;

  @Autowired private EmailVerificationTokenRepository emailVerificationTokenRepository;

  @Autowired private PasswordResetTokenRepository passwordResetTokenRepository;

  @Value("${app.jwt.access-secret}")
  private String accessSecret;

  @Test
  @DisplayName(
      "POST /api/auth/register should create user and set km_rt cookie with correct attributes")
  void testRegisterSuccessAndCookieAttributes() throws Exception {
    String uniqueEmail = "reg_" + System.currentTimeMillis() + "@khmermart.local";
    RegisterRequest request =
        new RegisterRequest(uniqueEmail, "ValidPass123", "New Customer", "+85512888999");

    ResponseEntity<String> response =
        restTemplate.postForEntity("/api/auth/register", request, String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    JsonNode json = objectMapper.readTree(response.getBody());
    assertThat(json.get("success").asBoolean()).isTrue();
    assertThat(json.get("data").get("user").get("email").asText()).isEqualTo(uniqueEmail);
    assertThat(json.get("data").has("accessToken")).isTrue();

    // Verify Set-Cookie header
    List<String> setCookies = response.getHeaders().get(HttpHeaders.SET_COOKIE);
    assertThat(setCookies).isNotNull().isNotEmpty();
    String cookieHeader = setCookies.get(0);
    assertThat(cookieHeader).contains("km_rt=");
    assertThat(cookieHeader).contains("HttpOnly");
    assertThat(cookieHeader).contains("SameSite=Lax");
    assertThat(cookieHeader).contains("Path=/api/auth");
    assertThat(cookieHeader).contains("Max-Age=604800");
  }

  @Test
  @DisplayName(
      "POST /api/auth/register validation failure should return 400 VALIDATION_ERROR with field details")
  void testRegisterValidationFailure() throws Exception {
    RegisterRequest invalidRequest = new RegisterRequest("bad-email", "short", "", "01234567");

    ResponseEntity<String> response =
        restTemplate.postForEntity("/api/auth/register", invalidRequest, String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    JsonNode json = objectMapper.readTree(response.getBody());
    assertThat(json.get("success").asBoolean()).isFalse();
    assertThat(json.get("error").get("code").asText()).isEqualTo("VALIDATION_ERROR");

    JsonNode details = json.get("error").get("details");
    assertThat(details).isNotNull();
    assertThat(details.has("email")).isTrue();
    assertThat(details.has("password")).isTrue();
    assertThat(details.has("phone")).isTrue();
    assertThat(details.has("fullName")).isTrue();
  }

  @Test
  @DisplayName("POST /api/auth/login success should return accessToken and km_rt cookie")
  void testLoginSuccess() throws Exception {
    AuthRequest loginRequest = new AuthRequest("customer1@khmermart.local", "Customer@123");

    ResponseEntity<String> response =
        restTemplate.postForEntity("/api/auth/login", loginRequest, String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    JsonNode json = objectMapper.readTree(response.getBody());
    assertThat(json.get("success").asBoolean()).isTrue();
    assertThat(json.get("data").get("accessToken").asText()).isNotEmpty();
    assertThat(json.get("data").get("user").get("email").asText())
        .isEqualTo("customer1@khmermart.local");

    List<String> cookies = response.getHeaders().get(HttpHeaders.SET_COOKIE);
    assertThat(cookies).isNotNull();
    assertThat(cookies.get(0)).contains("km_rt=");
  }

  @Test
  @DisplayName("6 wrong logins in a row should trigger 429 RATE_LIMITED")
  void testLoginRateLimiting() throws Exception {
    String testEmail = "brute_" + System.currentTimeMillis() + "@khmermart.local";
    AuthRequest wrongLogin = new AuthRequest(testEmail, "WrongPassword123");

    // Attempts 1 to 5: 401 UNAUTHORIZED
    for (int i = 1; i <= 5; i++) {
      ResponseEntity<String> resp =
          restTemplate.postForEntity("/api/auth/login", wrongLogin, String.class);
      assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
      JsonNode json = objectMapper.readTree(resp.getBody());
      assertThat(json.get("error").get("code").asText()).isEqualTo("UNAUTHORIZED");
    }

    // 6th attempt: 429 RATE_LIMITED
    ResponseEntity<String> rateLimitedResp =
        restTemplate.postForEntity("/api/auth/login", wrongLogin, String.class);
    assertThat(rateLimitedResp.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);

    JsonNode json = objectMapper.readTree(rateLimitedResp.getBody());
    assertThat(json.get("success").asBoolean()).isFalse();
    assertThat(json.get("error").get("code").asText()).isEqualTo("RATE_LIMITED");
  }

  @Test
  @DisplayName("POST /api/auth/refresh token rotation and reuse detection revoking family")
  void testRefreshTokenRotationAndReuseDetection() throws Exception {
    String email = "rotation_" + System.currentTimeMillis() + "@khmermart.local";
    RegisterRequest reg =
        new RegisterRequest(email, "Password123", "Rotation Test", "+85512333444");

    ResponseEntity<String> regResp =
        restTemplate.postForEntity("/api/auth/register", reg, String.class);
    assertThat(regResp.getStatusCode()).isEqualTo(HttpStatus.OK);

    String cookieHeader = regResp.getHeaders().getFirst(HttpHeaders.SET_COOKIE);
    assertThat(cookieHeader).isNotNull();
    String kmRtCookie = cookieHeader.split(";")[0]; // "km_rt=..."

    // 1st refresh call with the original cookie: SUCCEEDS
    HttpHeaders refreshHeaders = new HttpHeaders();
    refreshHeaders.add(HttpHeaders.COOKIE, kmRtCookie);
    HttpEntity<Void> refreshEntity = new HttpEntity<>(refreshHeaders);

    ResponseEntity<String> firstRefresh =
        restTemplate.exchange("/api/auth/refresh", HttpMethod.POST, refreshEntity, String.class);
    assertThat(firstRefresh.getStatusCode()).isEqualTo(HttpStatus.OK);

    String newCookieHeader = firstRefresh.getHeaders().getFirst(HttpHeaders.SET_COOKIE);
    assertThat(newCookieHeader).isNotNull();
    String newKmRtCookie = newCookieHeader.split(";")[0];
    assertThat(newKmRtCookie).isNotEqualTo(kmRtCookie);

    // 2nd refresh call with the SAME OLD cookie: FAILS (REUSE DETECTED)
    ResponseEntity<String> secondRefresh =
        restTemplate.exchange("/api/auth/refresh", HttpMethod.POST, refreshEntity, String.class);
    assertThat(secondRefresh.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

    JsonNode reuseJson = objectMapper.readTree(secondRefresh.getBody());
    assertThat(reuseJson.get("success").asBoolean()).isFalse();
    assertThat(reuseJson.get("error").get("code").asText()).isEqualTo("UNAUTHORIZED");

    // Also verify Set-Cookie: km_rt=; Max-Age=0 is sent on failure
    List<String> reuseCookies = secondRefresh.getHeaders().get(HttpHeaders.SET_COOKIE);
    assertThat(reuseCookies).isNotNull();
    assertThat(reuseCookies.get(0)).contains("Max-Age=0");

    // 3rd refresh call with the NEW cookie: FAILS because the whole family is now revoked!
    HttpHeaders newHeaders = new HttpHeaders();
    newHeaders.add(HttpHeaders.COOKIE, newKmRtCookie);
    ResponseEntity<String> thirdRefresh =
        restTemplate.exchange(
            "/api/auth/refresh", HttpMethod.POST, new HttpEntity<>(newHeaders), String.class);
    assertThat(thirdRefresh.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  @Test
  @DisplayName("Expired access token returns 401 TOKEN_EXPIRED envelope")
  void testExpiredAccessTokenReturns401TokenExpired() throws Exception {
    Instant past = Instant.now().minusSeconds(3600);
    String expiredToken =
        Jwts.builder()
            .subject("1")
            .claim("email", "test@khmermart.local")
            .claim("role", "CUSTOMER")
            .issuedAt(Date.from(past.minusSeconds(60)))
            .expiration(Date.from(past))
            .signWith(Keys.hmacShaKeyFor(accessSecret.getBytes(StandardCharsets.UTF_8)))
            .compact();

    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth(expiredToken);
    HttpEntity<Void> entity = new HttpEntity<>(headers);

    ResponseEntity<String> response =
        restTemplate.exchange("/api/users/me", HttpMethod.GET, entity, String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    JsonNode json = objectMapper.readTree(response.getBody());
    assertThat(json.get("success").asBoolean()).isFalse();
    assertThat(json.get("error").get("code").asText()).isEqualTo("TOKEN_EXPIRED");
    assertThat(json.get("error").get("message").asText()).isEqualTo("Token has expired");
  }

  @Test
  @DisplayName("CUSTOMER calling GET /api/admin/dashboard returns 403 FORBIDDEN envelope")
  void testCustomerCallingAdminDashboardReturns403Forbidden() throws Exception {
    AuthRequest login = new AuthRequest("customer1@khmermart.local", "Customer@123");
    ResponseEntity<String> loginResp =
        restTemplate.postForEntity("/api/auth/login", login, String.class);
    assertThat(loginResp.getStatusCode()).isEqualTo(HttpStatus.OK);

    JsonNode loginJson = objectMapper.readTree(loginResp.getBody());
    String accessToken = loginJson.get("data").get("accessToken").asText();

    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth(accessToken);
    HttpEntity<Void> entity = new HttpEntity<>(headers);

    ResponseEntity<String> response =
        restTemplate.exchange("/api/admin/dashboard", HttpMethod.GET, entity, String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    JsonNode json = objectMapper.readTree(response.getBody());
    assertThat(json.get("success").asBoolean()).isFalse();
    assertThat(json.get("error").get("code").asText()).isEqualTo("FORBIDDEN");
  }

  @Test
  @DisplayName(
      "User profile CRUD: GET /api/users/me, PATCH /api/users/me, PATCH /api/users/me/password, wrong password validation")
  void testUserProfileEndpoints() throws Exception {
    String email = "profile_" + System.currentTimeMillis() + "@khmermart.local";
    RegisterRequest reg =
        new RegisterRequest(email, "InitialPass123", "Initial Name", "+85512777888");

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
    assertThat(getMeJson.get("data").get("fullName").asText()).isEqualTo("Initial Name");

    // 2. PATCH /api/users/me
    AppUserUpdateRequest updateReq = new AppUserUpdateRequest("Updated Name", "+85598111222");
    HttpEntity<AppUserUpdateRequest> updateEntity = new HttpEntity<>(updateReq, headers);
    ResponseEntity<String> patchResp =
        restTemplate.exchange("/api/users/me", HttpMethod.PATCH, updateEntity, String.class);
    assertThat(patchResp.getStatusCode()).isEqualTo(HttpStatus.OK);
    JsonNode patchJson = objectMapper.readTree(patchResp.getBody());
    assertThat(patchJson.get("data").get("fullName").asText()).isEqualTo("Updated Name");
    assertThat(patchJson.get("data").get("phone").asText()).isEqualTo("+85598111222");

    // 3. PATCH /api/users/me/password with WRONG currentPassword -> 400 VALIDATION_ERROR on field
    // currentPassword
    PasswordRequest wrongPassReq = new PasswordRequest("WrongCurrentPass", "NewSecurePass456");
    ResponseEntity<String> wrongPassResp =
        restTemplate.exchange(
            "/api/users/me/password",
            HttpMethod.PATCH,
            new HttpEntity<>(wrongPassReq, headers),
            String.class);
    assertThat(wrongPassResp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    JsonNode wrongPassJson = objectMapper.readTree(wrongPassResp.getBody());
    assertThat(wrongPassJson.get("error").get("code").asText()).isEqualTo("VALIDATION_ERROR");
    assertThat(wrongPassJson.get("error").get("details").has("currentPassword")).isTrue();

    // 4. PATCH /api/users/me/password with CORRECT currentPassword -> 200 OK
    PasswordRequest changePassReq = new PasswordRequest("InitialPass123", "NewSecurePass456");
    ResponseEntity<String> passResp =
        restTemplate.exchange(
            "/api/users/me/password",
            HttpMethod.PATCH,
            new HttpEntity<>(changePassReq, headers),
            String.class);
    assertThat(passResp.getStatusCode()).isEqualTo(HttpStatus.OK);

    // 5. Verify login works with new password
    AuthRequest newLogin = new AuthRequest(email, "NewSecurePass456");
    ResponseEntity<String> loginResp =
        restTemplate.postForEntity("/api/auth/login", newLogin, String.class);
    assertThat(loginResp.getStatusCode()).isEqualTo(HttpStatus.OK);
  }

  @Test
  @DisplayName("POST /api/auth/logout clears km_rt cookie and revokes refresh token")
  void testLogout() throws Exception {
    String email = "logout_" + System.currentTimeMillis() + "@khmermart.local";
    RegisterRequest reg = new RegisterRequest(email, "Password123", "Logout Test", "+85512777999");
    ResponseEntity<String> regResp =
        restTemplate.postForEntity("/api/auth/register", reg, String.class);
    String cookieHeader = regResp.getHeaders().getFirst(HttpHeaders.SET_COOKIE);
    assertThat(cookieHeader).isNotNull();
    String kmRtCookie = cookieHeader.split(";")[0];

    HttpHeaders logoutHeaders = new HttpHeaders();
    logoutHeaders.add(HttpHeaders.COOKIE, kmRtCookie);
    ResponseEntity<String> logoutResp =
        restTemplate.exchange(
            "/api/auth/logout", HttpMethod.POST, new HttpEntity<>(logoutHeaders), String.class);
    assertThat(logoutResp.getStatusCode()).isEqualTo(HttpStatus.OK);

    List<String> cookies = logoutResp.getHeaders().get(HttpHeaders.SET_COOKIE);
    assertThat(cookies).isNotNull();
    assertThat(cookies.get(0)).contains("Max-Age=0");

    // Refresh after logout should fail
    ResponseEntity<String> refreshAfterLogout =
        restTemplate.exchange(
            "/api/auth/refresh", HttpMethod.POST, new HttpEntity<>(logoutHeaders), String.class);
    assertThat(refreshAfterLogout.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
  }
}
