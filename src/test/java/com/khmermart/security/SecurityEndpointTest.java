package com.khmermart.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.khmermart.AbstractIntegrationTest;
import com.khmermart.model.enumerate.Roles;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
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

class SecurityEndpointTest extends AbstractIntegrationTest {

  @Autowired private TestRestTemplate restTemplate;

  @Autowired private JwtService jwtService;

  @Autowired private ObjectMapper objectMapper;

  @Autowired private org.springframework.context.ApplicationContext context;

  @Value("${app.jwt.access-secret}")
  private String accessSecret;

  @Test
  @DisplayName(
      "Unauthenticated request to protected endpoint should return 401 UNAUTHORIZED contract envelope")
  void unauthenticatedReturnsUnauthorizedEnvelope() throws Exception {
    ResponseEntity<String> response = restTemplate.getForEntity("/api/users/me", String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    JsonNode json = objectMapper.readTree(response.getBody());
    assertThat(json.get("success").asBoolean()).isFalse();
    assertThat(json.has("error")).isTrue();
    JsonNode error = json.get("error");
    assertThat(error.get("code").asText()).isEqualTo("UNAUTHORIZED");
    assertThat(error.get("message").asText()).isEqualTo("Authentication required");
    assertThat(error.has("error")).isFalse();
  }

  @Test
  @DisplayName("Expired token should return 401 TOKEN_EXPIRED contract envelope")
  void expiredTokenReturnsTokenExpiredEnvelope() throws Exception {
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
    assertThat(json.has("error")).isTrue();
    JsonNode error = json.get("error");
    assertThat(error.get("code").asText()).isEqualTo("TOKEN_EXPIRED");
    assertThat(error.get("message").asText()).isEqualTo("Token has expired");
    assertThat(error.has("error")).isFalse();
  }

  @Test
  @DisplayName("CUSTOMER accessing admin endpoint should return 403 FORBIDDEN contract envelope")
  void customerAccessingAdminReturnsForbiddenEnvelope() throws Exception {
    String customerToken =
        jwtService.generateAccessToken(2L, "customer@khmermart.local", Roles.CUSTOMER);

    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth(customerToken);
    HttpEntity<Void> entity = new HttpEntity<>(headers);

    ResponseEntity<String> response =
        restTemplate.exchange("/api/admin/dashboard", HttpMethod.GET, entity, String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    JsonNode json = objectMapper.readTree(response.getBody());
    assertThat(json.get("success").asBoolean()).isFalse();
    assertThat(json.get("error").get("code").asText()).isEqualTo("FORBIDDEN");
  }

  @Test
  @DisplayName("Swagger OpenAPI docs should be publicly accessible")
  void swaggerUiIsPublic() {
    ResponseEntity<String> response = restTemplate.getForEntity("/api/docs", String.class);
    assertThat(response.getStatusCode().value()).isIn(200, 301, 302, 307, 308);

    ResponseEntity<String> apiDocsResponse =
        restTemplate.getForEntity("/api/v3/api-docs", String.class);
    assertThat(apiDocsResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(apiDocsResponse.getBody()).contains("openapi");
  }

  @Test
  @DisplayName(
      "No default in-memory user: HTTP Basic is rejected with 401 and no UserDetailsService")
  void basicAuthRejectedAndNoDefaultUser() throws Exception {
    HttpHeaders headers = new HttpHeaders();
    headers.set("Authorization", "Basic dXNlcjp4"); // user:x
    ResponseEntity<String> response =
        restTemplate.exchange(
            "/api/admin/dashboard", HttpMethod.GET, new HttpEntity<>(headers), String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(response.getHeaders().get(HttpHeaders.WWW_AUTHENTICATE)).isNull();
    assertThat(objectMapper.readTree(response.getBody()).at("/error/code").asText())
        .isEqualTo("UNAUTHORIZED");
    assertThat(
            context.getBeanNamesForType(
                org.springframework.security.core.userdetails.UserDetailsService.class))
        .isEmpty();
  }
}
