package com.khmermart.config;

import com.khmermart.modules.khqr.model.KhqrMode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "app")
@Validated
public record AppProperties(
    @NotBlank String frontendUrl,
    @NotBlank String publicApiUrl,
    @NotBlank String uploadDir,
    @Valid @NotNull JwtProperties jwt,
    @Valid @NotNull MailProperties mail,
    @Valid @NotNull VapidProperties vapid,
    @Valid @NotNull KhqrProperties khqr) {

  public record JwtProperties(
      @NotBlank String accessSecret,
      @NotBlank String refreshSecret,
      @NotNull Duration accessTtl,
      @NotNull Duration refreshTtl) {}

  public record MailProperties(@NotBlank String from) {}

  public record VapidProperties(String publicKey, String privateKey, String subject) {}

  public record KhqrProperties(
      @NotNull KhqrMode mode,
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
      int usdKhrRate) {
    @AssertTrue(message = "BAKONG_TOKEN and BAKONG_ACCOUNT_ID are required when KHQR mode is LIVE")
    public boolean isValidLiveMode() {
      if (mode == KhqrMode.LIVE) {
        return token != null && !token.isBlank() && accountId != null && !accountId.isBlank();
      }
      return true;
    }
  }
}
