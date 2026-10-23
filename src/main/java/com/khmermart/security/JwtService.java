package com.khmermart.security;

import com.khmermart.config.AppProperties;
import com.khmermart.modules.user.model.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

  private final SecretKey accessKey;
  private final SecretKey refreshKey;
  private final AppProperties appProperties;

  public JwtService(AppProperties appProperties) {
    this.appProperties = appProperties;
    this.accessKey =
        Keys.hmacShaKeyFor(appProperties.jwt().accessSecret().getBytes(StandardCharsets.UTF_8));
    this.refreshKey =
        Keys.hmacShaKeyFor(appProperties.jwt().refreshSecret().getBytes(StandardCharsets.UTF_8));
  }

  public String generateAccessToken(Long userId, String email, Role role) {
    Instant now = Instant.now();
    Instant expiry = now.plus(appProperties.jwt().accessTtl());

    return Jwts.builder()
        .subject(String.valueOf(userId))
        .claim("email", email)
        .claim("role", role.name())
        .issuedAt(Date.from(now))
        .expiration(Date.from(expiry))
        .signWith(accessKey)
        .compact();
  }

  public String generateRefreshToken(Long userId, String familyId) {
    Instant now = Instant.now();
    Instant expiry = now.plus(appProperties.jwt().refreshTtl());

    return Jwts.builder()
        .subject(String.valueOf(userId))
        .claim("familyId", familyId)
        .issuedAt(Date.from(now))
        .expiration(Date.from(expiry))
        .signWith(refreshKey)
        .compact();
  }

  public String generateStreamToken(Long paymentId, Long userId) {
    Instant now = Instant.now();
    Instant expiry = now.plusSeconds(15 * 60);

    return Jwts.builder()
        .subject(String.valueOf(paymentId))
        .claim("userId", userId)
        .claim("typ", "stream")
        .issuedAt(Date.from(now))
        .expiration(Date.from(expiry))
        .signWith(accessKey)
        .compact();
  }

  public Claims parseAccessToken(String token) {
    return Jwts.parser().verifyWith(accessKey).build().parseSignedClaims(token).getPayload();
  }

  public Claims parseRefreshToken(String token) {
    return Jwts.parser().verifyWith(refreshKey).build().parseSignedClaims(token).getPayload();
  }
}
