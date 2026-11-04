package com.khmermart.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.khmermart.model.request.AuthRequest;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.cache.CacheManager;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Boots the app against its OWN database (khmermart_clean) with app.seed.mode=clean, so the
 * truncate done by the seeder cannot touch the demo data other test classes rely on.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {"app.seed.reset=true", "app.seed.mode=clean"})
@ActiveProfiles("test")
class CleanStoreIntegrationTest {

  private static final org.testcontainers.containers.PostgreSQLContainer<?> postgres =
      com.khmermart.AbstractIntegrationTest.postgres;

  private static final org.testcontainers.containers.GenericContainer<?> redis =
      com.khmermart.AbstractIntegrationTest.redis;

  private static final String CLEAN_DB = "khmermart_clean";

  @DynamicPropertySource
  static void cleanDatabase(DynamicPropertyRegistry registry) throws Exception {
    try (Connection c =
            DriverManager.getConnection(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        Statement st = c.createStatement()) {
      boolean exists =
          st.executeQuery("SELECT 1 FROM pg_database WHERE datname = '" + CLEAN_DB + "'").next();
      if (!exists) {
        st.execute("CREATE DATABASE " + CLEAN_DB);
      }
    }
    registry.add(
        "spring.datasource.url",
        () -> postgres.getJdbcUrl().replace("/" + postgres.getDatabaseName(), "/" + CLEAN_DB));
    registry.add("spring.datasource.username", postgres::getUsername);
    registry.add("spring.datasource.password", postgres::getPassword);
    registry.add("spring.data.redis.host", () -> redis.isRunning() ? redis.getHost() : "localhost");
    registry.add(
        "spring.data.redis.port", () -> redis.isRunning() ? redis.getMappedPort(6379) : 6379);
  }

  @Autowired private TestRestTemplate restTemplate;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private CacheManager cacheManager;

  /** Redis is shared with the demo-data contexts; never leave empty-store pages cached there. */
  private void clearCaches() {
    cacheManager.getCacheNames().forEach(n -> cacheManager.getCache(n).clear());
  }

  private long count(String table) {
    return jdbc.queryForObject("SELECT count(*) FROM " + table, Long.class);
  }

  @Test
  @DisplayName("Clean seed: only admin, 25 provinces, 3 shipping methods and settings exist")
  void cleanSeedContainsOnlyEssentials() {
    assertThat(jdbc.queryForObject("SELECT current_database()", String.class)).isEqualTo(CLEAN_DB);
    assertThat(count("users")).isEqualTo(1);
    Map<String, Object> admin = jdbc.queryForMap("SELECT email, role FROM users");
    assertThat(admin.get("email")).isEqualTo("admin@khmermart.local");
    assertThat(admin.get("role")).isEqualTo("ADMIN");
    assertThat(count("provinces")).isEqualTo(25);
    assertThat(jdbc.queryForList("SELECT code FROM shipping_methods ORDER BY code", String.class))
        .containsExactly("EXPRESS", "SAME_DAY", "STANDARD");
    assertThat(
            jdbc.queryForObject("SELECT value FROM settings WHERE key='STORE_NAME'", String.class))
        .isEqualTo("KhmerMart");
    assertThat(
            jdbc.queryForObject(
                "SELECT value FROM settings WHERE key='USD_KHR_RATE'", String.class))
        .isEqualTo("4100");
    assertThat(
            jdbc.queryForObject("SELECT value FROM settings WHERE key='KHQR_MODE'", String.class))
        .isEqualTo("MOCK");

    for (String t :
        List.of(
            "products",
            "categories",
            "orders",
            "order_items",
            "reviews",
            "coupons",
            "promotions",
            "notifications",
            "addresses",
            "payments",
            "inventory",
            "carts")) {
      assertThat(count(t)).as(t).isZero();
    }
  }

  @Test
  @DisplayName("Empty store: public and admin endpoints return valid empty 200 responses")
  void emptyStoreEndpoints() throws Exception {
    clearCaches();
    try {
      ResponseEntity<String> login =
          restTemplate.postForEntity(
              "/api/auth/login",
              new AuthRequest("admin@khmermart.local", "Admin@12345"),
              String.class);
      assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);
      HttpHeaders admin = new HttpHeaders();
      admin.setBearerAuth(
          objectMapper.readTree(login.getBody()).get("data").get("accessToken").asText());

      for (String url :
          List.of(
              "/api/home",
              "/api/products",
              "/api/products?page=1&size=12&sort=newest",
              "/api/categories",
              "/api/search/suggest?q=silk",
              "/api/shipping/methods",
              "/api/settings/public")) {
        assertOk(url, new HttpHeaders());
      }
      for (String url :
          List.of(
              "/api/admin/dashboard",
              "/api/admin/dashboard?range=7d",
              "/api/admin/products",
              "/api/admin/categories",
              "/api/admin/orders",
              "/api/admin/payments",
              "/api/admin/inventory",
              "/api/admin/coupons",
              "/api/admin/promotions",
              "/api/admin/reviews",
              "/api/admin/users",
              "/api/admin/reports/sales?groupBy=day",
              "/api/admin/reports/sales?groupBy=month",
              "/api/admin/reports/sales?groupBy=category",
              "/api/admin/reports/sales?groupBy=product",
              "/api/admin/reports/sales?groupBy=paymentMethod",
              "/api/admin/reports/sales.csv?groupBy=day",
              "/api/admin/reports/sales.xlsx?groupBy=day",
              "/api/admin/products/export.csv",
              "/api/admin/payments/export.csv",
              "/api/admin/payments/reconciliation")) {
        assertOk(url, admin);
      }

      JsonNode home =
          objectMapper.readTree(restTemplate.getForEntity("/api/home", String.class).getBody());
      assertThat(home.get("success").asBoolean()).isTrue();
      JsonNode products =
          objectMapper
              .readTree(restTemplate.getForEntity("/api/products", String.class).getBody())
              .get("data");
      assertThat(products.isArray()).isTrue();
      assertThat(products).isEmpty();
    } finally {
      clearCaches();
    }
  }

  private void assertOk(String url, HttpHeaders headers) {
    ResponseEntity<byte[]> res =
        restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(headers), byte[].class);
    assertThat(res.getStatusCode().value()).as("GET " + url).isEqualTo(200);
  }
}
