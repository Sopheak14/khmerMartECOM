package com.khmermart.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.khmermart.AbstractIntegrationTest;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;

/**
 * Runs scripts/contract_check_full.py against this build's own OpenAPI spec (no running backend
 * needed): every endpoint in the contract script and docs/API_CONTRACT.md must exist.
 */
class ApiContractCheckTest extends AbstractIntegrationTest {

  @Autowired private TestRestTemplate rt;

  @Test
  @DisplayName("contract_check_full.py passes against the built OpenAPI spec")
  void contractMatchesControllers() throws Exception {
    Path script = Path.of("scripts/contract_check_full.py");
    Assumptions.assumeTrue(Files.exists(script) && python() != null, "python3 / script missing");

    String spec = rt.getForObject("/api/v3/api-docs", String.class);
    assertThat(spec).contains("/api/orders/{orderNumber}/cancel-request");
    Path file = Path.of("target/api-docs.json").toAbsolutePath();
    Files.writeString(file, spec, StandardCharsets.UTF_8);

    Process p =
        new ProcessBuilder(python(), script.toString(), file.toUri().toString())
            .redirectErrorStream(true)
            .start();
    String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    int exit = p.waitFor();
    assertThat(exit).as(out).isZero();
  }

  private static String python() {
    try {
      Process p = new ProcessBuilder("python3", "--version").start();
      return p.waitFor() == 0 ? "python3" : null;
    } catch (Exception e) {
      return null;
    }
  }
}
