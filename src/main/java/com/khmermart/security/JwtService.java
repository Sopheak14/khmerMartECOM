package com.khmermart.security;

import com.khmermart.config.AppProperty;
import com.khmermart.model.enumerate.Roles;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

  private final AppProperty appProperties;
  private final SecretKey accessKey;

  public JwtService(AppProperty appProperties) {
    this.appProperties = appProperties;
    this.accessKey =
        Keys.hmacShaKeyFor(appProperties.jwt().accessSecret().getBytes(StandardCharsets.UTF_8));
  }

  public String generateAccessToken(Long userId, String email, Roles role) {
    Date now = new Date();
    Date expiry = new Date(now.getTime() + appProperties.jwt().accessTtl().toMillis());

    return Jwts.builder()
        .subject(String.valueOf(userId))
        .claim("email", email)
        .claim("role", role.name())
        .issuedAt(now)
        .expiration(expiry)
        .signWith(accessKey)
        .compact();
  }

  public String generateStreamToken(Long paymentId, Long userId) {
    Date now = new Date();
    Date expiry = new Date(now.getTime() + 15 * 60 * 1000L); // 15 min

    return Jwts.builder()
        .subject(String.valueOf(userId))
        .claim("paymentId", paymentId)
        .claim("typ", "stream")
        .issuedAt(now)
        .expiration(expiry)
        .signWith(accessKey)
        .compact();
  }

  public boolean validateStreamToken(String token, Long paymentId) {
    try {
      Claims claims = validateAndGetClaims(token);
      if (!"stream".equals(claims.get("typ", String.class))) {
        return false;
      }
      Long tokenPaymentId = claims.get("paymentId", Long.class);
      if (tokenPaymentId == null) {
        Number num = claims.get("paymentId", Number.class);
        tokenPaymentId = num != null ? num.longValue() : null;
      }
      return paymentId != null && paymentId.equals(tokenPaymentId);
    } catch (Exception e) {
      return false;
    }
  }

  /** Returns the user id a (valid) stream token was issued to, or null if invalid. */
  public Long streamTokenUserId(String token, Long paymentId) {
    if (!validateStreamToken(token, paymentId)) {
      return null;
    }
    try {
      return Long.valueOf(validateAndGetClaims(token).getSubject());
    } catch (Exception e) {
      return null;
    }
  }

  public Claims validateAndGetClaims(String token) {
    return Jwts.parser().verifyWith(accessKey).build().parseSignedClaims(token).getPayload();
  }

  public boolean isTokenExpired(String token) {
    try {
      validateAndGetClaims(token);
      return false;
    } catch (ExpiredJwtException e) {
      return true;
    } catch (JwtException e) {
      return false;
    }
  }
}
