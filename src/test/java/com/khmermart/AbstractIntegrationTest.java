package com.khmermart;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.khmermart.model.request.RegisterRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Testcontainers
public abstract class AbstractIntegrationTest {

  public static final PostgreSQLContainer<?> postgres =
      new PostgreSQLContainer<>("postgres:16-alpine")
          .withDatabaseName("khmermart_test")
          .withUsername("khmermart")
          .withPassword("khmermart");

  public static final GenericContainer<?> redis =
      new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

  @Autowired protected JdbcTemplate jdbc;

  /** Marks a throwaway test account verified (the OTP flow itself is covered in OtpFlowTest). */
  protected void markVerified(String email) {
    jdbc.update(
        "UPDATE users SET email_verified = true, email_verified_at = now() WHERE email = ?",
        email.toLowerCase());
  }

  protected long userId(String email) {
    return jdbc.queryForObject(
        "SELECT id FROM users WHERE email = ?", Long.class, email.toLowerCase());
  }

  /** register -> mark verified in DB -> login; returns the access token. */
  protected String registerVerifiedAndLogin(TestRestTemplate rt, RegisterRequest reg)
      throws Exception {
    ResponseEntity<String> r = rt.postForEntity("/api/auth/register", reg, String.class);
    if (!r.getStatusCode().is2xxSuccessful()) {
      throw new IllegalStateException("register failed: " + r.getBody());
    }
    markVerified(reg.email());
    ResponseEntity<String> l =
        rt.postForEntity(
            "/api/auth/login",
            java.util.Map.of("email", reg.email(), "password", reg.password()),
            String.class);
    return new ObjectMapper().readTree(l.getBody()).get("data").get("accessToken").asText();
  }

  static {
    postgres.start();
    try {
      redis.start();
    } catch (Exception ignored) {
      // fallback to host redis if container fails to bind
    }
  }

  @DynamicPropertySource
  static void configureProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", postgres::getJdbcUrl);
    registry.add("spring.datasource.username", postgres::getUsername);
    registry.add("spring.datasource.password", postgres::getPassword);
    if (redis.isRunning()) {
      registry.add("spring.data.redis.host", redis::getHost);
      registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    } else {
      registry.add("spring.data.redis.host", () -> "localhost");
      registry.add("spring.data.redis.port", () -> 6379);
    }
  }
}
