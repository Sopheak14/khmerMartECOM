package com.khmermart.security;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.Optional;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

@Component
public class CookieUtil {

  public static final String REFRESH_TOKEN_COOKIE_NAME = "km_rt";
  public static final String GUEST_CART_COOKIE_NAME = "km_guest";
  public static final String AUTH_COOKIE_PATH = "/api/auth";

  private final boolean isProd;

  public CookieUtil(Environment env) {
    this.isProd = env.acceptsProfiles(Profiles.of("prod"));
  }

  public ResponseCookie createRefreshTokenCookie(String token, long maxAgeSeconds) {
    return ResponseCookie.from(REFRESH_TOKEN_COOKIE_NAME, token)
        .httpOnly(true)
        .secure(isProd)
        .sameSite("Lax")
        .path(AUTH_COOKIE_PATH)
        .maxAge(maxAgeSeconds)
        .build();
  }

  public ResponseCookie deleteRefreshTokenCookie() {
    return ResponseCookie.from(REFRESH_TOKEN_COOKIE_NAME, "")
        .httpOnly(true)
        .secure(isProd)
        .sameSite("Lax")
        .path(AUTH_COOKIE_PATH)
        .maxAge(0)
        .build();
  }

  public Optional<String> getRefreshTokenFromCookie(HttpServletRequest request) {
    if (request.getCookies() == null) {
      return Optional.empty();
    }
    return Arrays.stream(request.getCookies())
        .filter(c -> REFRESH_TOKEN_COOKIE_NAME.equals(c.getName()))
        .map(Cookie::getValue)
        .filter(val -> val != null && !val.isBlank())
        .findFirst();
  }
}
