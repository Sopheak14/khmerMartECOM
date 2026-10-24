package com.khmermart.service.impl;

import com.khmermart.common.constant.MessageConstant;
import com.khmermart.common.exception.CustomException;
import com.khmermart.common.exception.ErrorCode;
import com.khmermart.common.response.TokenResponse;
import com.khmermart.config.AppProperty;
import com.khmermart.mapper.AppUserMapper;
import com.khmermart.model.entity.AppUser;
import com.khmermart.model.entity.AppUserToken;
import com.khmermart.model.entity.EmailVerificationToken;
import com.khmermart.model.entity.PasswordResetToken;
import com.khmermart.model.enumerate.Roles;
import com.khmermart.model.request.AuthRequest;
import com.khmermart.model.request.ForgotPasswordRequest;
import com.khmermart.model.request.RegisterRequest;
import com.khmermart.model.request.ResetPasswordRequest;
import com.khmermart.model.request.VerifyEmailRequest;
import com.khmermart.model.response.AppUserDto;
import com.khmermart.repository.AppUserRepository;
import com.khmermart.repository.AppUserTokenRepository;
import com.khmermart.repository.EmailVerificationTokenRepository;
import com.khmermart.repository.PasswordResetTokenRepository;
import com.khmermart.security.CookieUtil;
import com.khmermart.security.JwtService;
import com.khmermart.security.LoginRateLimiter;
import com.khmermart.service.AppUserTokenService;
import com.khmermart.service.AuthService;
import com.khmermart.service.MailService;
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
public class AuthServiceImpl implements AuthService {

  private static final Logger log = LoggerFactory.getLogger(AuthServiceImpl.class);

  private final AppUserRepository appUserRepository;
  private final AppUserTokenRepository appUserTokenRepository;
  private final AppUserTokenService appUserTokenService;
  private final PasswordResetTokenRepository passwordResetTokenRepository;
  private final EmailVerificationTokenRepository emailVerificationTokenRepository;
  private final PasswordEncoder passwordEncoder;
  private final JwtService jwtService;
  private final CookieUtil cookieUtil;
  private final LoginRateLimiter loginRateLimiter;
  private final MailService mailService;
  private final AppProperty appProperties;
  private final AppUserMapper appUserMapper;

  public AuthServiceImpl(
      AppUserRepository appUserRepository,
      AppUserTokenRepository appUserTokenRepository,
      AppUserTokenService appUserTokenService,
      PasswordResetTokenRepository passwordResetTokenRepository,
      EmailVerificationTokenRepository emailVerificationTokenRepository,
      PasswordEncoder passwordEncoder,
      JwtService jwtService,
      CookieUtil cookieUtil,
      LoginRateLimiter loginRateLimiter,
      MailService mailService,
      AppProperty appProperties,
      AppUserMapper appUserMapper) {
    this.appUserRepository = appUserRepository;
    this.appUserTokenRepository = appUserTokenRepository;
    this.appUserTokenService = appUserTokenService;
    this.passwordResetTokenRepository = passwordResetTokenRepository;
    this.emailVerificationTokenRepository = emailVerificationTokenRepository;
    this.passwordEncoder = passwordEncoder;
    this.jwtService = jwtService;
    this.cookieUtil = cookieUtil;
    this.loginRateLimiter = loginRateLimiter;
    this.mailService = mailService;
    this.appProperties = appProperties;
    this.appUserMapper = appUserMapper;
  }

  @Override
  public AuthResult register(RegisterRequest request) {
    String email = request.email().toLowerCase().trim();
    if (appUserRepository.existsByEmail(email)) {
      throw new CustomException(ErrorCode.CONFLICT, MessageConstant.EMAIL_REGISTERED);
    }

    AppUser user =
        AppUser.builder()
            .email(email)
            .passwordHash(passwordEncoder.encode(request.password()))
            .fullName(request.fullName().trim())
            .phone(request.phone().trim())
            .role(Roles.CUSTOMER)
            .active(true)
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .build();

    user = appUserRepository.save(user);

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

  @Override
  public AuthResult login(AuthRequest request, String clientIp) {
    String email = request.email().toLowerCase().trim();
    String rateLimitKey = clientIp + ":" + email;

    if (!loginRateLimiter.tryConsume(rateLimitKey)) {
      throw new CustomException(ErrorCode.RATE_LIMITED, MessageConstant.RATE_LIMITED);
    }

    AppUser user =
        appUserRepository
            .findByEmail(email)
            .orElseThrow(
                () ->
                    new CustomException(
                        ErrorCode.UNAUTHORIZED, MessageConstant.INVALID_CREDENTIALS));

    if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
      throw new CustomException(ErrorCode.UNAUTHORIZED, MessageConstant.INVALID_CREDENTIALS);
    }

    if (!user.isActive()) {
      throw new CustomException(ErrorCode.FORBIDDEN, "Account is deactivated");
    }

    loginRateLimiter.reset(rateLimitKey);
    return createSession(user);
  }

  @Override
  public AuthResult refresh(String rawToken) {
    if (rawToken == null || rawToken.isBlank()) {
      throw new CustomException(ErrorCode.UNAUTHORIZED, MessageConstant.REFRESH_TOKEN_REQUIRED);
    }

    String tokenHash = sha256(rawToken);
    AppUserToken oldToken =
        appUserTokenRepository
            .findByTokenHash(tokenHash)
            .orElseThrow(
                () ->
                    new CustomException(
                        ErrorCode.UNAUTHORIZED, MessageConstant.REFRESH_TOKEN_NO_LONGER_VALID));

    // Check if token has been revoked
    if (oldToken.getRevokedAt() != null) {
      // Real reuse of a rotated token (has been replaced by a successor)
      if (oldToken.getReplacedById() != null) {
        log.warn(
            "Refresh token reuse detected for familyId={}, userId={}",
            oldToken.getFamilyId(),
            oldToken.getUserId());
        appUserTokenService.revokeFamilyImmediately(oldToken.getFamilyId());
        throw new CustomException(ErrorCode.UNAUTHORIZED, MessageConstant.TOKEN_REUSE_DETECTED);
      }
      // Revoked/logged-out token or child in an already revoked family
      throw new CustomException(
          ErrorCode.UNAUTHORIZED, MessageConstant.REFRESH_TOKEN_NO_LONGER_VALID);
    }

    if (oldToken.getExpiresAt().isBefore(Instant.now())) {
      oldToken.setRevokedAt(Instant.now());
      appUserTokenRepository.save(oldToken);
      throw new CustomException(ErrorCode.TOKEN_EXPIRED, MessageConstant.TOKEN_EXPIRED);
    }

    AppUser user =
        appUserRepository
            .findById(oldToken.getUserId())
            .orElseThrow(
                () -> new CustomException(ErrorCode.UNAUTHORIZED, MessageConstant.USER_NOT_FOUND));

    if (!user.isActive()) {
      throw new CustomException(ErrorCode.FORBIDDEN, "Account is deactivated");
    }

    // Issue new token in the SAME family
    String newRawToken = UUID.randomUUID().toString();
    AppUserToken newToken =
        AppUserToken.builder()
            .userId(user.getId())
            .familyId(oldToken.getFamilyId())
            .tokenHash(sha256(newRawToken))
            .expiresAt(Instant.now().plus(appProperties.jwt().refreshTtl()))
            .build();
    newToken = appUserTokenRepository.save(newToken);

    // Revoke old token and link to new token
    oldToken.setRevokedAt(Instant.now());
    oldToken.setReplacedById(newToken.getId());
    appUserTokenRepository.save(oldToken);

    String accessToken =
        jwtService.generateAccessToken(user.getId(), user.getEmail(), user.getRole());
    AppUserDto userDTO = appUserMapper.toDTO(user);
    ResponseCookie cookie =
        cookieUtil.createRefreshTokenCookie(
            newRawToken, appProperties.jwt().refreshTtl().toSeconds());

    return new AuthResult(new TokenResponse(accessToken, userDTO), cookie);
  }

  @Override
  public ResponseCookie logout(String rawToken) {
    if (rawToken != null && !rawToken.isBlank()) {
      String tokenHash = sha256(rawToken);
      appUserTokenRepository
          .findByTokenHash(tokenHash)
          .ifPresent(
              token -> {
                token.setRevokedAt(Instant.now());
                appUserTokenRepository.save(token);
              });
    }
    return cookieUtil.deleteRefreshTokenCookie();
  }

  @Override
  public void forgotPassword(ForgotPasswordRequest request) {
    String email = request.email().toLowerCase().trim();
    appUserRepository
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

  @Override
  public void resetPassword(ResetPasswordRequest request) {
    String tokenHash = sha256(request.token().trim());
    PasswordResetToken resetToken =
        passwordResetTokenRepository
            .findByTokenHash(tokenHash)
            .orElseThrow(
                () ->
                    new CustomException(
                        ErrorCode.VALIDATION_ERROR, MessageConstant.INVALID_RESET_TOKEN));

    if (resetToken.getUsedAt() != null || resetToken.getExpiresAt().isBefore(Instant.now())) {
      throw new CustomException(ErrorCode.VALIDATION_ERROR, MessageConstant.INVALID_RESET_TOKEN);
    }

    AppUser user =
        appUserRepository
            .findById(resetToken.getUserId())
            .orElseThrow(
                () -> new CustomException(ErrorCode.NOT_FOUND, MessageConstant.USER_NOT_FOUND));

    user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
    user.setUpdatedAt(Instant.now());
    appUserRepository.save(user);

    resetToken.setUsedAt(Instant.now());
    passwordResetTokenRepository.save(resetToken);

    // Invalidate existing refresh tokens upon password reset by setting revokedAt
    appUserTokenRepository.revokeAllByUserId(user.getId(), Instant.now());
  }

  @Override
  public void verifyEmail(VerifyEmailRequest request) {
    String tokenHash = sha256(request.token().trim());
    EmailVerificationToken verificationToken =
        emailVerificationTokenRepository
            .findByTokenHash(tokenHash)
            .orElseThrow(
                () ->
                    new CustomException(
                        ErrorCode.VALIDATION_ERROR, MessageConstant.INVALID_VERIFY_TOKEN));

    if (verificationToken.getUsedAt() != null
        || verificationToken.getExpiresAt().isBefore(Instant.now())) {
      throw new CustomException(ErrorCode.VALIDATION_ERROR, MessageConstant.INVALID_VERIFY_TOKEN);
    }

    AppUser user =
        appUserRepository
            .findById(verificationToken.getUserId())
            .orElseThrow(
                () -> new CustomException(ErrorCode.NOT_FOUND, MessageConstant.USER_NOT_FOUND));

    user.setEmailVerifiedAt(Instant.now());
    user.setUpdatedAt(Instant.now());
    appUserRepository.save(user);

    verificationToken.setUsedAt(Instant.now());
    emailVerificationTokenRepository.save(verificationToken);
  }

  private AuthResult createSession(AppUser user) {
    String familyId = UUID.randomUUID().toString();
    String rawRefreshToken = UUID.randomUUID().toString();

    AppUserToken rt =
        AppUserToken.builder()
            .userId(user.getId())
            .familyId(familyId)
            .tokenHash(sha256(rawRefreshToken))
            .expiresAt(Instant.now().plus(appProperties.jwt().refreshTtl()))
            .build();
    appUserTokenRepository.save(rt);

    String accessToken =
        jwtService.generateAccessToken(user.getId(), user.getEmail(), user.getRole());
    AppUserDto userDTO = appUserMapper.toDTO(user);
    ResponseCookie cookie =
        cookieUtil.createRefreshTokenCookie(
            rawRefreshToken, appProperties.jwt().refreshTtl().toSeconds());

    return new AuthResult(new TokenResponse(accessToken, userDTO), cookie);
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
      throw new RuntimeException("SHA-256 algorithm not found", e);
    }
  }
}
