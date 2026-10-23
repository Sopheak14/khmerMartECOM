package com.khmermart.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "app")
@Validated
public record AppProperty(
    @NotBlank String frontendUrl,
    @NotBlank String publicApiUrl,
    @NotBlank String uploadDir,
    @NotNull Jwt jwt,
    @NotNull Mail mail,
    Vapid vapid,
    @NotNull Khqr khqr) {

  public record Jwt(
      @NotBlank String accessSecret,
      @NotBlank String refreshSecret,
      @NotNull Duration accessTtl,
      @NotNull Duration refreshTtl) {}

  public record Mail(@NotBlank String from) {}

  public record Vapid(String publicKey, String privateKey, String subject) {}

  public record Khqr(
      @NotBlank String mode,
      @NotBlank String type,
      @NotBlank String apiBase,
      String token,
      String tokenExpiresAt,
      @NotBlank String accountId,
      String merchantId,
      String acquiringBank,
      @NotBlank String merchantName,
      @NotBlank String merchantCity,
      @NotBlank String mobile,
      @NotBlank String storeLabel,
      @NotBlank String terminalLabel,
      String deeplinkUrl,
      @NotBlank String currency,
      int expireMinutes,
      int pollIntervalSeconds,
      long usdKhrRate) {}
}
