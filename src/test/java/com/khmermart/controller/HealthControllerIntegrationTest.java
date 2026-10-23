package com.khmermart.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.khmermart.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class HealthControllerIntegrationTest extends AbstractIntegrationTest {

  @Autowired private TestRestTemplate restTemplate;

  @Autowired private ObjectMapper objectMapper;

  @Test
  @DisplayName(
      "GET /api/health should return ok status and database connectivity matching SHARED API CONTRACT")
  void testHealthEndpoint() throws Exception {
    ResponseEntity<String> response = restTemplate.getForEntity("/api/health", String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isNotNull();

    JsonNode json = objectMapper.readTree(response.getBody());
    assertThat(json.get("success").asBoolean()).isTrue();
    assertThat(json.get("data").get("status").asText()).isEqualTo("ok");
    assertThat(json.get("data").get("db").asText()).isEqualTo("ok");
    assertThat(json.get("data").has("database")).isFalse();
    assertThat(json.get("data").get("redis").asText()).isEqualTo("ok");
    assertThat(json.get("data").get("khqrMode").asText()).isEqualTo("MOCK");

    // Assert exact contract JSON string matches
    String expectedJson =
        "{\"success\":true,\"data\":{\"status\":\"ok\",\"db\":\"ok\",\"redis\":\"ok\",\"khqrMode\":\"MOCK\"}}";
    assertThat(response.getBody()).isEqualTo(expectedJson);
  }
}
