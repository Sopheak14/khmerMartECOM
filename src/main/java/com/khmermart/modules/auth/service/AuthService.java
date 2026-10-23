package com.khmermart.modules.auth.service;

import com.khmermart.common.api.ApiException;
import com.khmermart.common.api.ErrorCode;
import com.khmermart.config.AppProperties;
import com.khmermart.modules.auth.dto.AuthResponse;
import com.khmermart.modules.auth.dto.ForgotPasswordRequest;
import com.khmermart.modules.auth.dto.LoginRequest;
import com.khmermart.modules.auth.dto.RegisterRequest;
import com.khmermart.modules.auth.dto.ResetPasswordRequest;
import com.khmermart.modules.auth.dto.VerifyEmailRequest;
import com.khmermart.modules.notification.service.MailService;
import com.khmermart.modules.user.dto.UserDTO;
import com.khmermart.modules.user.entity.EmailVerificationToken;
import com.khmermart.modules.user.entity.PasswordResetToken;
import com.khmermart.modules.user.entity.RefreshToken;
import com.khmermart.modules.user.entity.User;
import com.khmermart.modules.user.mapper.UserMapper;
import com.khmermart.modules.user.model.Role;
import com.khmermart.modules.user.repository.EmailVerificationTokenRepository;
import com.khmermart.modules.user.repository.PasswordResetTokenRepository;
import com.khmermart.modules.user.repository.RefreshTokenRepository;
import com.khmermart.modules.user.repository.UserRepository;
import com.khmermart.security.CookieUtil;
import com.khmermart.security.JwtService;
import com.khmermart.security.LoginRateLimiter;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseCookie;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class AuthService {

  private static final Logger log = LoggerFactory.getLogger(AuthService.class);

  private final UserRepository userRepository;
  private final RefreshTokenRepository refreshTokenRepository;
  private final RefreshTokenService refreshTokenService;
  private final PasswordResetTokenRepository passwordResetTokenRepository;
  private final EmailVerificationTokenRepository emailVerificationTokenRepository;
  private final PasswordEncoder passwordEncoder;
  private final JwtService jwtService;
  private final CookieUtil cookieUtil;
  private final LoginRateLimiter loginRateLimiter;
  private final MailService mailService;
  private final AppProperties appProperties;
  private final UserMapper userMapper;

  public AuthService(
      UserRepository userRepository,
      RefreshTokenRepository refreshTokenRepository,
      RefreshTokenService refreshTokenService,
      PasswordResetTokenRepository passwordResetTokenRepository,
      EmailVerificationTokenRepository emailVerificationTokenRepository,
      PasswordEncoder passwordEncoder,
      JwtService jwtService,
      CookieUtil cookieUtil,
      LoginRateLimiter loginRateLimiter,
      MailService mailService,
      AppProperties appProperties,
      UserMapper userMapper) {
    this.userRepository = userRepository;
    this.refreshTokenRepository = refreshTokenRepository;
    this.refreshTokenService = refreshTokenService;
    this.passwordResetTokenRepository = passwordResetTokenRepository;
    this.emailVerificationTokenRepository = emailVerificationTokenRepository;
    this.passwordEncoder = passwordEncoder;
    this.jwtService = jwtService;
    this.cookieUtil = cookieUtil;
    this.loginRateLimiter = loginRateLimiter;
    this.mailService = mailService;
    this.appProperties = appProperties;
    this.userMapper = userMapper;
  }

  public record AuthResult(AuthResponse authResponse, ResponseCookie cookie) {}

  public AuthResult register(RegisterRequest request) {
    String email = request.email().toLowerCase().trim();
    if (userRepository.existsByEmail(email)) {
      throw new ApiException(ErrorCode.CONFLICT, "Email is already registered");
    }

    User user =
        User.builder()
            .email(email)
            .passwordHash(passwordEncoder.encode(request.password()))
            .fullName(request.fullName().trim())
            .phone(request.phone().trim())
            .role(Role.CUSTOMER)
            .active(true)
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .build();

    user = userRepository.save(user);

    // Create Email Verification Token
    String rawVerifyToken = UUID.randomUUID().toString();
    EmailVerificationToken verificationToken =
        EmailVerificationToken.builder()
            .userId(user.getId())
            .tokenHash(sha256(rawVerifyToken))
            .expiresAt(Instant.now().plus(Duration.ofHours(24)))
            .build();
    emailVerificationTokenRepository.save(verificationToken);

    mailService.sendVerificationEmail(user.getEmail(), user.getFullName(), rawVerifyToken);

    // Issue initial session
    return createSession(user);
  }

  public AuthResult login(LoginRequest request, String clientIp) {
    String email = request.email().toLowerCase().trim();
    String rateLimitKey = clientIp + ":" + email;

    if (!loginRateLimiter.tryConsume(rateLimitKey)) {
      throw new ApiException(
          ErrorCode.RATE_LIMITED, "Too many login attempts. Please try again later");
    }

    User user =
        userRepository
            .findByEmail(email)
            .orElseThrow(
                () -> new ApiException(ErrorCode.UNAUTHORIZED, "Invalid email or password"));

    if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
      throw new ApiException(ErrorCode.UNAUTHORIZED, "Invalid email or password");
    }

    if (!user.isActive()) {
      throw new ApiException(ErrorCode.FORBIDDEN, "Account is disabled");
    }

    loginRateLimiter.reset(rateLimitKey);
    return createSession(user);
  }

  public AuthResult refresh(String rawToken) {
    if (rawToken == null || rawToken.isBlank()) {
      throw new ApiException(ErrorCode.UNAUTHORIZED, "Refresh token required");
    }

    String tokenHash = sha256(rawToken);
    RefreshToken oldToken =
        refreshTokenRepository
            .findByTokenHash(tokenHash)
            .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHORIZED, "Invalid refresh token"));

    // Reuse detection: If the token has already been revoked, revoke the whole family
    if (oldToken.getRevokedAt() != null) {
      log.warn(
          "Refresh token reuse detected for familyId={}, userId={}",
          oldToken.getFamilyId(),
          oldToken.getUserId());
      refreshTokenService.revokeFamilyImmediately(oldToken.getFamilyId());
      throw new ApiException(ErrorCode.UNAUTHORIZED, "Invalid refresh token: token reuse detected");
    }

    if (oldToken.getExpiresAt().isBefore(Instant.now())) {
      oldToken.setRevokedAt(Instant.now());
      refreshTokenRepository.save(oldToken);
      throw new ApiException(ErrorCode.TOKEN_EXPIRED, "Refresh token has expired");
    }

    User user =
        userRepository
            .findById(oldToken.getUserId())
            .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHORIZED, "User not found"));

    if (!user.isActive()) {
      throw new ApiException(ErrorCode.FORBIDDEN, "Account is disabled");
    }

    // Issue new token in the SAME family
    String newRawToken = UUID.randomUUID().toString();
    RefreshToken newToken =
        RefreshToken.builder()
            .userId(user.getId())
            .familyId(oldToken.getFamilyId())
            .tokenHash(sha256(newRawToken))
            .expiresAt(Instant.now().plus(appProperties.jwt().refreshTtl()))
            .build();
    newToken = refreshTokenRepository.save(newToken);

    // Revoke old token and link to new token
    oldToken.setRevokedAt(Instant.now());
    oldToken.setReplacedById(newToken.getId());
    refreshTokenRepository.save(oldToken);

    String accessToken =
        jwtService.generateAccessToken(user.getId(), user.getEmail(), user.getRole());
    UserDTO userDTO = userMapper.toDTO(user);
    ResponseCookie cookie =
        cookieUtil.createRefreshTokenCookie(
            newRawToken, appProperties.jwt().refreshTtl().toSeconds());

    return new AuthResult(new AuthResponse(accessToken, userDTO), cookie);
  }

  public ResponseCookie logout(String rawToken) {
    if (rawToken != null && !rawToken.isBlank()) {
      String tokenHash = sha256(rawToken);
      refreshTokenRepository
          .findByTokenHash(tokenHash)
          .ifPresent(
              token -> {
                token.setRevokedAt(Instant.now());
                refreshTokenRepository.save(token);
              });
    }
    return cookieUtil.deleteRefreshTokenCookie();
  }

  public void forgotPassword(ForgotPasswordRequest request) {
    String email = request.email().toLowerCase().trim();
    userRepository
        .findByEmail(email)
        .ifPresent(
            user -> {
              if (user.isActive()) {
                String rawToken = UUID.randomUUID().toString();
                PasswordResetToken resetToken =
                    PasswordResetToken.builder()
                        .userId(user.getId())
                        .tokenHash(sha256(rawToken))
                        .expiresAt(Instant.now().plus(Duration.ofMinutes(30)))
                        .build();
                passwordResetTokenRepository.save(resetToken);
                mailService.sendPasswordResetEmail(user.getEmail(), user.getFullName(), rawToken);
              }
            });
  }

  public void resetPassword(ResetPasswordRequest request) {
    String tokenHash = sha256(request.token().trim());
    PasswordResetToken resetToken =
        passwordResetTokenRepository
            .findByTokenHash(tokenHash)
            .orElseThrow(
                () ->
                    new ApiException(ErrorCode.VALIDATION_ERROR, "Invalid or expired reset token"));

    if (resetToken.getUsedAt() != null || resetToken.getExpiresAt().isBefore(Instant.now())) {
      throw new ApiException(ErrorCode.VALIDATION_ERROR, "Invalid or expired reset token");
    }

    User user =
        userRepository
            .findById(resetToken.getUserId())
            .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "User not found"));

    user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
    user.setUpdatedAt(Instant.now());
    userRepository.save(user);

    resetToken.setUsedAt(Instant.now());
    passwordResetTokenRepository.save(resetToken);

    // Invalidate existing refresh tokens upon password reset
    refreshTokenRepository.deleteByUserId(user.getId());
  }

  public void verifyEmail(VerifyEmailRequest request) {
    String tokenHash = sha256(request.token().trim());
    EmailVerificationToken verificationToken =
        emailVerificationTokenRepository
            .findByTokenHash(tokenHash)
            .orElseThrow(
                () ->
                    new ApiException(
                        ErrorCode.VALIDATION_ERROR, "Invalid or expired verification token"));

    if (verificationToken.getUsedAt() != null
        || verificationToken.getExpiresAt().isBefore(Instant.now())) {
      throw new ApiException(ErrorCode.VALIDATION_ERROR, "Invalid or expired verification token");
    }

    User user =
        userRepository
            .findById(verificationToken.getUserId())
            .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "User not found"));

    user.setEmailVerifiedAt(Instant.now());
    user.setUpdatedAt(Instant.now());
    userRepository.save(user);

    verificationToken.setUsedAt(Instant.now());
    emailVerificationTokenRepository.save(verificationToken);
  }

  private AuthResult createSession(User user) {
    String familyId = UUID.randomUUID().toString();
    String rawRefreshToken = UUID.randomUUID().toString();

    RefreshToken rt =
        RefreshToken.builder()
            .userId(user.getId())
            .familyId(familyId)
            .tokenHash(sha256(rawRefreshToken))
            .expiresAt(Instant.now().plus(appProperties.jwt().refreshTtl()))
            .build();
    refreshTokenRepository.save(rt);

    String accessToken =
        jwtService.generateAccessToken(user.getId(), user.getEmail(), user.getRole());
    UserDTO userDTO = userMapper.toDTO(user);
    ResponseCookie cookie =
        cookieUtil.createRefreshTokenCookie(
            rawRefreshToken, appProperties.jwt().refreshTtl().toSeconds());

    return new AuthResult(new AuthResponse(accessToken, userDTO), cookie);
  }

  private String sha256(String input) {
    try {
      MessageDigest md = MessageDigest.getInstance("SHA-256");
      byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
      StringBuilder hexString = new StringBuilder();
      for (byte b : hash) {
        String hex = Integer.toHexString(0xff & b);
        if (hex.length() == 1) {
          hexString.append('0');
        }
        hexString.append(hex);
      }
      return hexString.toString();
    } catch (NoSuchAlgorithmException e) {
      throw new RuntimeException("SHA-256 algorithm not available", e);
    }
  }
}
