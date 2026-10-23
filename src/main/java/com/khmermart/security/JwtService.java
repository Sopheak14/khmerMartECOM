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
