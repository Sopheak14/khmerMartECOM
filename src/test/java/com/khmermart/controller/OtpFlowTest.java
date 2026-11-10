package com.khmermart.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.khmermart.AbstractIntegrationTest;
import com.khmermart.service.MailService;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/** Email verification by 6-digit OTP. Codes are read from the captured outgoing mail call. */
class OtpFlowTest extends AbstractIntegrationTest {

  private static final String PASSWORD = "OtpPass12345";

  @Autowired private TestRestTemplate rt;
  @Autowired private ObjectMapper om;
  @SpyBean private MailService mailService;

  private String newEmail() {
    return "otp-test-" + UUID.randomUUID() + "@test.local";
  }

  private ResponseEntity<String> register(String email) {
    return rt.postForEntity(
        "/api/auth/register",
        Map.of("email", email, "password", PASSWORD, "fullName", "Otp Tester", "phone", "+85512345678"),
        String.class);
  }

  private ResponseEntity<String> verify(String email, String code) {
    return rt.postForEntity(
        "/api/auth/verify-email", Map.of("email", email, "code", code), String.class);
  }

  private ResponseEntity<String> resend(String email) {
    return rt.postForEntity("/api/auth/resend-verification", Map.of("email", email), String.class);
  }

  private ResponseEntity<String> login(String email) {
    return rt.postForEntity(
        "/api/auth/login", Map.of("email", email, "password", PASSWORD), String.class);
  }

  /** The n-th (1-based) code mailed to this address, waiting for the async after-commit send. */
  private String codeSentTo(String email, int nth) throws Exception {
    for (int i = 0; i < 100; i++) {
      List<String> codes =
          Mockito.mockingDetails(mailService).getInvocations().stream()
              .filter(inv -> inv.getMethod().getName().equals("sendVerificationCodeEmail"))
              .filter(inv -> email.equals(inv.getArgument(0)))
              .map(inv -> (String) inv.getArgument(2))
              .toList();
      if (codes.size() >= nth) {
        return codes.get(nth - 1);
      }
      Thread.sleep(100);
    }
    throw new AssertionError("no OTP mail captured for " + email);
  }

  private JsonNode body(ResponseEntity<String> r) throws Exception {
    return om.readTree(r.getBody());
  }

  private String wrongCode(String real) {
    return real.equals("000000") ? "111111" : "000000";
  }

  @Test
  @DisplayName("register: user is unverified, mail with a 6-digit code is sent, no tokens, hash only")
  void registerCreatesUnverifiedUserAndSendsMail() throws Exception {
    String email = newEmail();
    ResponseEntity<String> r = register(email);

    assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
    JsonNode data = body(r).get("data");
    assertThat(data.get("email").asText()).isEqualTo(email);
    assertThat(data.get("verificationRequired").asBoolean()).isTrue();
    assertThat(data.get("expiresInSeconds").asInt()).isEqualTo(600);
    assertThat(data.get("resendInSeconds").asInt()).isEqualTo(60);
    assertThat(data.has("accessToken")).isFalse();
    assertThat(r.getHeaders().get(HttpHeaders.SET_COOKIE)).isNull();

    Boolean verified =
        jdbc.queryForObject("SELECT email_verified FROM users WHERE email = ?", Boolean.class, email);
    assertThat(verified).isFalse();

    String code = codeSentTo(email, 1);
    assertThat(code).matches("\\d{6}");
    String hash =
        jdbc.queryForObject(
            "SELECT code_hash FROM email_otp WHERE user_id = ?", String.class, userId(email));
    assertThat(hash).hasSize(64).isNotEqualTo(code);
  }

  @Test
  @DisplayName("verify-email with the correct code verifies the user and signs them in")
  void verifyCorrectCodeSignsIn() throws Exception {
    String email = newEmail();
    register(email);
    ResponseEntity<String> r = verify(email, codeSentTo(email, 1));

    assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
    JsonNode data = body(r).get("data");
    assertThat(data.get("accessToken").asText()).isNotBlank();
    assertThat(data.get("user").get("emailVerified").asBoolean()).isTrue();
    assertThat(r.getHeaders().getFirst(HttpHeaders.SET_COOKIE)).contains("km_rt=");

    Boolean verified =
        jdbc.queryForObject("SELECT email_verified FROM users WHERE email = ?", Boolean.class, email);
    assertThat(verified).isTrue();
    Boolean consumed =
        jdbc.queryForObject(
            "SELECT consumed_at IS NOT NULL FROM email_otp WHERE user_id = ?",
            Boolean.class,
            userId(email));
    assertThat(consumed).isTrue();

    // a second verify is now a conflict
    ResponseEntity<String> again = verify(email, "123456");
    assertThat(again.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(body(again).get("error").get("code").asText()).isEqualTo("EMAIL_ALREADY_VERIFIED");
  }

  @Test
  @DisplayName("wrong codes decrement attemptsLeft; the 5th wrong one is 429 and locks the code")
  void wrongCodesThenLockout() throws Exception {
    String email = newEmail();
    register(email);
    String real = codeSentTo(email, 1);
    String bad = wrongCode(real);

    for (int expectedLeft = 4; expectedLeft >= 1; expectedLeft--) {
      ResponseEntity<String> r = verify(email, bad);
      assertThat(r.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
      JsonNode err = body(r).get("error");
      assertThat(err.get("code").asText()).isEqualTo("OTP_INVALID");
      assertThat(err.get("details").get("attemptsLeft").asInt()).isEqualTo(expectedLeft);
    }
    ResponseEntity<String> fifth = verify(email, bad);
    assertThat(fifth.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    assertThat(body(fifth).get("error").get("code").asText()).isEqualTo("OTP_TOO_MANY_ATTEMPTS");

    // even the correct code is refused until a new one is requested
    ResponseEntity<String> locked = verify(email, real);
    assertThat(locked.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    assertThat(body(locked).get("error").get("code").asText()).isEqualTo("OTP_TOO_MANY_ATTEMPTS");
  }

  @Test
  @DisplayName("an expired code is rejected with OTP_EXPIRED")
  void expiredCode() throws Exception {
    String email = newEmail();
    register(email);
    String code = codeSentTo(email, 1);
    jdbc.update(
        "UPDATE email_otp SET expires_at = now() - interval '1 minute' WHERE user_id = ?",
        userId(email));

    ResponseEntity<String> r = verify(email, code);
    assertThat(r.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(body(r).get("error").get("code").asText()).isEqualTo("OTP_EXPIRED");
  }

  @Test
  @DisplayName("resend has a 60 s cooldown; a new code invalidates the old one and resets attempts")
  void resendCooldownAndInvalidation() throws Exception {
    String email = newEmail();
    register(email);
    String oldCode = codeSentTo(email, 1);
    verify(email, wrongCode(oldCode)); // burn one attempt

    ResponseEntity<String> tooSoon = resend(email);
    assertThat(tooSoon.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    JsonNode err = body(tooSoon).get("error");
    assertThat(err.get("code").asText()).isEqualTo("OTP_RESEND_COOLDOWN");
    assertThat(err.get("details").get("retryInSeconds").asInt()).isBetween(1, 60);

    jdbc.update(
        "UPDATE email_otp SET last_sent_at = now() - interval '2 minutes' WHERE user_id = ?",
        userId(email));
    ResponseEntity<String> ok = resend(email);
    assertThat(ok.getStatusCode()).isEqualTo(HttpStatus.OK);
    String newCode = codeSentTo(email, 2);
    Integer attempts =
        jdbc.queryForObject(
            "SELECT attempts FROM email_otp WHERE user_id = ?", Integer.class, userId(email));
    assertThat(attempts).isZero();

    if (!newCode.equals(oldCode)) { // 1-in-a-million collision would make the old code valid
      ResponseEntity<String> old = verify(email, oldCode);
      assertThat(old.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
      assertThat(body(old).get("error").get("code").asText()).isEqualTo("OTP_INVALID");
    }
    assertThat(verify(email, newCode).getStatusCode()).isEqualTo(HttpStatus.OK);
  }

  @Test
  @DisplayName("login: unverified -> 403 EMAIL_NOT_VERIFIED with no tokens; verified -> 200")
  void loginRequiresVerifiedEmail() throws Exception {
    String email = newEmail();
    register(email);

    ResponseEntity<String> blocked = login(email);
    assertThat(blocked.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    JsonNode err = body(blocked).get("error");
    assertThat(err.get("code").asText()).isEqualTo("EMAIL_NOT_VERIFIED");
    assertThat(err.get("details").get("email").asText()).isEqualTo(email);
    assertThat(blocked.getHeaders().get(HttpHeaders.SET_COOKIE)).isNull();
    assertThat(blocked.getBody()).doesNotContain("accessToken");

    verify(email, codeSentTo(email, 1));
    ResponseEntity<String> ok = login(email);
    assertThat(ok.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(body(ok).get("data").get("accessToken").asText()).isNotBlank();
  }

  @Test
  @DisplayName("resend-verification gives the same 200 answer for unknown and verified emails")
  void resendDoesNotEnumerateUsers() throws Exception {
    ResponseEntity<String> unknown = resend(newEmail());
    assertThat(unknown.getStatusCode()).isEqualTo(HttpStatus.OK);

    String email = newEmail();
    register(email);
    verify(email, codeSentTo(email, 1));
    ResponseEntity<String> verified = resend(email);
    assertThat(verified.getStatusCode()).isEqualTo(HttpStatus.OK);

    assertThat(body(unknown).get("data").get("message").asText())
        .isEqualTo(body(verified).get("data").get("message").asText());
  }

  @Test
  @DisplayName("verify-email for an unknown email looks like a wrong code")
  void verifyUnknownEmail() throws Exception {
    ResponseEntity<String> r = verify(newEmail(), "123456");
    assertThat(r.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(body(r).get("error").get("code").asText()).isEqualTo("OTP_INVALID");
  }

  @Test
  @DisplayName("migration V7: users that exist before it are marked verified")
  void migrationMarksExistingUsersVerified() throws Exception {
    String url = postgres.getJdbcUrl().replace("/" + postgres.getDatabaseName(), "/v7_migration");
    try (Connection c =
            DriverManager.getConnection(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        Statement st = c.createStatement()) {
      st.execute("CREATE DATABASE v7_migration");
    }
    Flyway.configure()
        .dataSource(url, postgres.getUsername(), postgres.getPassword())
        .target("6")
        .load()
        .migrate();
    try (Connection c =
            DriverManager.getConnection(url, postgres.getUsername(), postgres.getPassword());
        Statement st = c.createStatement()) {
      st.execute(
          "INSERT INTO users (email, password_hash, full_name, phone, role, created_at, updated_at)"
              + " VALUES ('old@test.local','x','Old','+85511111111','CUSTOMER', now(), now())");
    }
    Flyway.configure()
        .dataSource(url, postgres.getUsername(), postgres.getPassword())
        .load()
        .migrate();
    try (Connection c =
            DriverManager.getConnection(url, postgres.getUsername(), postgres.getPassword());
        Statement st = c.createStatement()) {
      var rs = st.executeQuery("SELECT email_verified, email_verified_at FROM users");
      assertThat(rs.next()).isTrue();
      assertThat(rs.getBoolean(1)).isTrue();
      assertThat(rs.getTimestamp(2)).isNotNull();
    }
  }
}
