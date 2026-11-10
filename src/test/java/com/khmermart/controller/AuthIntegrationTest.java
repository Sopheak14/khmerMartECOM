package com.khmermart.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.khmermart.AbstractIntegrationTest;
import com.khmermart.model.entity.AppUser;
import com.khmermart.model.entity.AppUserToken;
import com.khmermart.model.entity.PasswordResetToken;
import com.khmermart.model.request.AuthRequest;
import com.khmermart.model.request.ForgotPasswordRequest;
import com.khmermart.model.request.RegisterRequest;
import com.khmermart.model.request.ResetPasswordRequest;
import com.khmermart.repository.AppUserRepository;
import com.khmermart.repository.AppUserTokenRepository;
import com.khmermart.repository.PasswordResetTokenRepository;
import com.khmermart.security.JwtService;
import com.khmermart.service.MailService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.mock.mockito.SpyBean;
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

  @Autowired private AppUserRepository appUserRepository;

  @Autowired private AppUserTokenRepository appUserTokenRepository;

  @Autowired private PasswordResetTokenRepository passwordResetTokenRepository;

  @SpyBean private MailService mailService;

  @Value("${app.jwt.access-secret}")
  private String accessSecret;

  @Test
  @DisplayName("POST /api/auth/register creates an unverified user and returns no tokens/cookie")
  void testRegisterSuccessReturnsNoTokens() throws Exception {
    String uniqueEmail = "reg_" + System.currentTimeMillis() + "@khmermart.local";
    RegisterRequest request =
        new RegisterRequest(uniqueEmail, "ValidPass123", "New Customer", "+85512888999");

    ResponseEntity<String> response =
        restTemplate.postForEntity("/api/auth/register", request, String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    JsonNode json = objectMapper.readTree(response.getBody());
    assertThat(json.get("success").asBoolean()).isTrue();
    assertThat(json.get("data").has("accessToken")).isFalse();
    assertThat(json.get("data").get("email").asText()).isEqualTo(uniqueEmail);
    assertThat(response.getHeaders().get(HttpHeaders.SET_COOKIE)).isNull();
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
    assertThat(details.get("password").asText())
        .isEqualTo("Password must be at least 8 characters and contain both letters and numbers");
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

  /** register -> mark verified -> login; returns the km_rt Set-Cookie header. */
  private String registerVerifyLoginCookie(RegisterRequest reg) {
    ResponseEntity<String> regResp =
        restTemplate.postForEntity("/api/auth/register", reg, String.class);
    assertThat(regResp.getStatusCode()).isEqualTo(HttpStatus.OK);
    markVerified(reg.email());
    ResponseEntity<String> login =
        restTemplate.postForEntity(
            "/api/auth/login", new AuthRequest(reg.email(), reg.password()), String.class);
    assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);
    return login.getHeaders().getFirst(HttpHeaders.SET_COOKIE);
  }

  @Test
  @DisplayName("POST /api/auth/refresh token rotation and reuse detection revoking family")
  void testRefreshTokenRotationAndReuseDetection() throws Exception {
    String email = "rotation_" + System.currentTimeMillis() + "@khmermart.local";
    RegisterRequest reg =
        new RegisterRequest(email, "Password123", "Rotation Test", "+85512333444");

    String cookieHeader = registerVerifyLoginCookie(reg);
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
    assertThat(reuseJson.get("error").get("message").asText()).contains("token reuse detected");

    // Verify Set-Cookie: km_rt=; Max-Age=0 is sent on failure
    List<String> reuseCookies = secondRefresh.getHeaders().get(HttpHeaders.SET_COOKIE);
    assertThat(reuseCookies).isNotNull();
    assertThat(reuseCookies.get(0)).contains("Max-Age=0");

    // 3rd refresh call with the NEW cookie: FAILS because the whole family was revoked!
    HttpHeaders newHeaders = new HttpHeaders();
    newHeaders.add(HttpHeaders.COOKIE, newKmRtCookie);
    ResponseEntity<String> thirdRefresh =
        restTemplate.exchange(
            "/api/auth/refresh", HttpMethod.POST, new HttpEntity<>(newHeaders), String.class);
    assertThat(thirdRefresh.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    JsonNode thirdJson = objectMapper.readTree(thirdRefresh.getBody());
    assertThat(thirdJson.get("error").get("message").asText())
        .isEqualTo("Refresh token is no longer valid");
  }

  @Test
  @DisplayName(
      "Forgot password -> Reset password flow (token single-use, old password fails, revokes all refresh tokens)")
  void testForgotPasswordAndResetPasswordFlow() throws Exception {
    String email = "pwd_reset_" + System.currentTimeMillis() + "@khmermart.local";
    RegisterRequest reg =
        new RegisterRequest(email, "InitialPassword123", "Reset Tester", "+85512444999");
    ResponseEntity<String> regResp =
        restTemplate.postForEntity("/api/auth/register", reg, String.class);
    assertThat(regResp.getStatusCode()).isEqualTo(HttpStatus.OK);
    markVerified(email);

    AppUser user = appUserRepository.findByEmail(email).orElseThrow();
    Long userId = user.getId();

    // 1. Request forgot password
    ForgotPasswordRequest forgotReq = new ForgotPasswordRequest(email);
    ResponseEntity<String> forgotResp =
        restTemplate.postForEntity("/api/auth/forgot-password", forgotReq, String.class);
    assertThat(forgotResp.getStatusCode()).isEqualTo(HttpStatus.OK);
    JsonNode forgotJson = objectMapper.readTree(forgotResp.getBody());
    assertThat(forgotJson.get("success").asBoolean()).isTrue();
    assertThat(forgotJson.has("data")).isTrue();
    assertThat(forgotJson.get("data").isObject()).isTrue();

    // 2. Capture raw token sent via mailService
    ArgumentCaptor<String> tokenCaptor = ArgumentCaptor.forClass(String.class);
    verify(mailService, atLeastOnce())
        .sendPasswordResetEmail(eq(email), any(), tokenCaptor.capture());
    String rawResetToken = tokenCaptor.getValue();
    assertThat(rawResetToken).isNotBlank();

    // Verify token stored in DB is hashed with SHA-256 (never plaintext)
    List<PasswordResetToken> tokens = passwordResetTokenRepository.findAll();
    PasswordResetToken dbToken =
        tokens.stream().filter(t -> t.getUserId().equals(userId)).findFirst().orElseThrow();
    assertThat(dbToken.getTokenHash()).hasSize(64);
    assertThat(dbToken.getTokenHash()).isNotEqualTo(rawResetToken);

    // 3. Reset password using valid raw token
    ResetPasswordRequest resetReq = new ResetPasswordRequest(rawResetToken, "NewSecurePassword456");
    ResponseEntity<String> resetResp =
        restTemplate.postForEntity("/api/auth/reset-password", resetReq, String.class);
    assertThat(resetResp.getStatusCode()).isEqualTo(HttpStatus.OK);
    JsonNode resetJson = objectMapper.readTree(resetResp.getBody());
    assertThat(resetJson.get("success").asBoolean()).isTrue();
    assertThat(resetJson.has("data")).isTrue();
    assertThat(resetJson.get("data").isObject()).isTrue();

    // 4. Second use of same reset token fails (single-use)
    ResponseEntity<String> secondResetResp =
        restTemplate.postForEntity("/api/auth/reset-password", resetReq, String.class);
    assertThat(secondResetResp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

    // 5. Old password no longer works
    AuthRequest oldLogin = new AuthRequest(email, "InitialPassword123");
    ResponseEntity<String> oldLoginResp =
        restTemplate.postForEntity("/api/auth/login", oldLogin, String.class);
    assertThat(oldLoginResp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

    // 6. New password works
    AuthRequest newLogin = new AuthRequest(email, "NewSecurePassword456");
    ResponseEntity<String> newLoginResp =
        restTemplate.postForEntity("/api/auth/login", newLogin, String.class);
    assertThat(newLoginResp.getStatusCode()).isEqualTo(HttpStatus.OK);

    // 7. Verify all previous refresh tokens of the user are revoked
    List<AppUserToken> userTokens = appUserTokenRepository.findAll();
    long unrevokedCount =
        userTokens.stream()
            .filter(t -> t.getUserId().equals(userId))
            .filter(t -> t.getRevokedAt() == null)
            .count();
    // Only the single brand new login token from step 6 should be unrevoked
    assertThat(unrevokedCount).isEqualTo(1);
  }

  @Test
  @DisplayName("POST /api/auth/logout clears km_rt cookie and revokes refresh token")
  void testLogout() throws Exception {
    String email = "logout_" + System.currentTimeMillis() + "@khmermart.local";
    RegisterRequest reg = new RegisterRequest(email, "Password123", "Logout Test", "+85512777999");
    String cookieHeader = registerVerifyLoginCookie(reg);
    assertThat(cookieHeader).isNotNull();
    String kmRtCookie = cookieHeader.split(";")[0];

    HttpHeaders logoutHeaders = new HttpHeaders();
    logoutHeaders.add(HttpHeaders.COOKIE, kmRtCookie);
    ResponseEntity<String> logoutResp =
        restTemplate.exchange(
            "/api/auth/logout", HttpMethod.POST, new HttpEntity<>(logoutHeaders), String.class);
    assertThat(logoutResp.getStatusCode()).isEqualTo(HttpStatus.OK);
    JsonNode logoutJson = objectMapper.readTree(logoutResp.getBody());
    assertThat(logoutJson.has("data")).isTrue();
    assertThat(logoutJson.get("data").isObject()).isTrue();

    List<String> cookies = logoutResp.getHeaders().get(HttpHeaders.SET_COOKIE);
    assertThat(cookies).isNotNull();
    assertThat(cookies.get(0)).contains("Max-Age=0");

    // Refresh after logout should fail with "Refresh token is no longer valid"
    ResponseEntity<String> refreshAfterLogout =
        restTemplate.exchange(
            "/api/auth/refresh", HttpMethod.POST, new HttpEntity<>(logoutHeaders), String.class);
    assertThat(refreshAfterLogout.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    JsonNode refreshJson = objectMapper.readTree(refreshAfterLogout.getBody());
    assertThat(refreshJson.get("error").get("message").asText())
        .isEqualTo("Refresh token is no longer valid");
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
}
