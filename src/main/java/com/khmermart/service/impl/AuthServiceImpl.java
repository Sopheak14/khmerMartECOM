package com.khmermart.service.impl;

import com.khmermart.common.constant.MessageConstant;
import com.khmermart.common.exception.CustomException;
import com.khmermart.common.exception.ErrorCode;
import com.khmermart.common.response.TokenResponse;
import com.khmermart.config.AppProperty;
import com.khmermart.mapper.AppUserMapper;
import com.khmermart.model.entity.AppUser;
import com.khmermart.model.entity.AppUserToken;
import com.khmermart.event.EmailOtpRequestedEvent;
import com.khmermart.model.entity.EmailOtp;
import com.khmermart.model.entity.PasswordResetToken;
import com.khmermart.model.enumerate.Roles;
import com.khmermart.model.request.AuthRequest;
import com.khmermart.model.request.ForgotPasswordRequest;
import com.khmermart.model.request.RegisterRequest;
import com.khmermart.model.request.ResendVerificationRequest;
import com.khmermart.model.request.ResetPasswordRequest;
import com.khmermart.model.request.VerifyEmailRequest;
import com.khmermart.model.response.AppUserDto;
import com.khmermart.repository.AppUserRepository;
import com.khmermart.repository.AppUserTokenRepository;
import com.khmermart.repository.EmailOtpRepository;
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
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.ResponseCookie;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class AuthServiceImpl implements AuthService {

  private static final Logger log = LoggerFactory.getLogger(AuthServiceImpl.class);

  private static final int OTP_TTL_SECONDS = 600;
  private static final int RESEND_COOLDOWN_SECONDS = 60;
  private static final int OTP_MAX_ATTEMPTS = 5;

  private final AppUserRepository appUserRepository;
  private final AppUserTokenRepository appUserTokenRepository;
  private final AppUserTokenService appUserTokenService;
  private final PasswordResetTokenRepository passwordResetTokenRepository;
  private final EmailOtpRepository emailOtpRepository;
  private final PasswordEncoder passwordEncoder;
  private final JwtService jwtService;
  private final CookieUtil cookieUtil;
  private final LoginRateLimiter loginRateLimiter;
  private final MailService mailService;
  private final AppProperty appProperties;
  private final AppUserMapper appUserMapper;
  private final ApplicationEventPublisher eventPublisher;
  private final String otpPepper;
  private final SecureRandom secureRandom = new SecureRandom();

  public AuthServiceImpl(
      AppUserRepository appUserRepository,
      AppUserTokenRepository appUserTokenRepository,
      AppUserTokenService appUserTokenService,
      PasswordResetTokenRepository passwordResetTokenRepository,
      EmailOtpRepository emailOtpRepository,
      PasswordEncoder passwordEncoder,
      JwtService jwtService,
      CookieUtil cookieUtil,
      LoginRateLimiter loginRateLimiter,
      MailService mailService,
      AppProperty appProperties,
      AppUserMapper appUserMapper,
      ApplicationEventPublisher eventPublisher,
      @Value("${app.otp.pepper}") String otpPepper) {
    this.appUserRepository = appUserRepository;
    this.appUserTokenRepository = appUserTokenRepository;
    this.appUserTokenService = appUserTokenService;
    this.passwordResetTokenRepository = passwordResetTokenRepository;
    this.emailOtpRepository = emailOtpRepository;
    this.passwordEncoder = passwordEncoder;
    this.jwtService = jwtService;
    this.cookieUtil = cookieUtil;
    this.loginRateLimiter = loginRateLimiter;
    this.mailService = mailService;
    this.appProperties = appProperties;
    this.appUserMapper = appUserMapper;
    this.eventPublisher = eventPublisher;
    this.otpPepper = otpPepper;
  }

  @Override
  public RegisterResult register(RegisterRequest request) {
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
            .emailVerified(false)
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .build();

    user = appUserRepository.save(user);
    issueOtp(user);

    return new RegisterResult(email, true, OTP_TTL_SECONDS, RESEND_COOLDOWN_SECONDS);
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

    if (!user.isEmailVerified()) {
      throw new CustomException(
          ErrorCode.EMAIL_NOT_VERIFIED, MessageConstant.EMAIL_NOT_VERIFIED, Map.of("email", email));
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

  /** Failed attempts must be persisted even though the request ends in an exception. */
  @Override
  @Transactional(noRollbackFor = CustomException.class)
  public AuthResult verifyEmail(VerifyEmailRequest request) {
    String email = request.email().toLowerCase().trim();
    AppUser user = appUserRepository.findByEmail(email).orElse(null);
    if (user == null) {
      // Same answer as a wrong code, so the endpoint does not reveal which emails exist.
      throw new CustomException(
          ErrorCode.OTP_INVALID,
          MessageConstant.OTP_INVALID,
          Map.of("attemptsLeft", OTP_MAX_ATTEMPTS - 1));
    }
    if (user.isEmailVerified()) {
      throw new CustomException(
          ErrorCode.EMAIL_ALREADY_VERIFIED, MessageConstant.EMAIL_ALREADY_VERIFIED);
    }

    EmailOtp otp = emailOtpRepository.findByUserId(user.getId()).orElse(null);
    if (otp == null || otp.getConsumedAt() != null) {
      throw new CustomException(ErrorCode.OTP_EXPIRED, MessageConstant.OTP_EXPIRED);
    }
    if (otp.getAttempts() >= OTP_MAX_ATTEMPTS) {
      throw tooManyAttempts();
    }
    if (otp.getExpiresAt().isBefore(Instant.now())) {
      throw new CustomException(ErrorCode.OTP_EXPIRED, MessageConstant.OTP_EXPIRED);
    }

    String candidate = hashCode(user.getId(), request.code().trim());
    boolean match =
        MessageDigest.isEqual(
            candidate.getBytes(StandardCharsets.UTF_8),
            otp.getCodeHash().getBytes(StandardCharsets.UTF_8));
    if (!match) {
      otp.setAttempts(otp.getAttempts() + 1);
      emailOtpRepository.save(otp);
      int left = OTP_MAX_ATTEMPTS - otp.getAttempts();
      if (left <= 0) {
        throw tooManyAttempts();
      }
      throw new CustomException(
          ErrorCode.OTP_INVALID, MessageConstant.OTP_INVALID, Map.of("attemptsLeft", left));
    }

    Instant now = Instant.now();
    otp.setConsumedAt(now);
    emailOtpRepository.save(otp);
    user.setEmailVerified(true);
    user.setEmailVerifiedAt(now);
    user.setUpdatedAt(now);
    appUserRepository.save(user);
    return createSession(user);
  }

  @Override
  public ResendResult resendVerification(ResendVerificationRequest request) {
    String email = request.email().toLowerCase().trim();
    AppUser user = appUserRepository.findByEmail(email).orElse(null);
    if (user != null && user.isActive() && !user.isEmailVerified()) {
      EmailOtp existing = emailOtpRepository.findByUserId(user.getId()).orElse(null);
      if (existing != null) {
        long elapsed = Duration.between(existing.getLastSentAt(), Instant.now()).getSeconds();
        if (elapsed < RESEND_COOLDOWN_SECONDS) {
          throw new CustomException(
              ErrorCode.OTP_RESEND_COOLDOWN,
              MessageConstant.OTP_RESEND_COOLDOWN,
              Map.of("retryInSeconds", RESEND_COOLDOWN_SECONDS - elapsed));
        }
      }
      issueOtp(user);
    }
    // Identical answer for unknown / already verified emails: no user enumeration.
    return new ResendResult(MessageConstant.OTP_RESEND_GENERIC, OTP_TTL_SECONDS, RESEND_COOLDOWN_SECONDS);
  }

  private CustomException tooManyAttempts() {
    return new CustomException(
        ErrorCode.OTP_TOO_MANY_ATTEMPTS,
        MessageConstant.OTP_TOO_MANY_ATTEMPTS,
        Map.of("attemptsLeft", 0));
  }

  /** Creates/overwrites the user's code (old code invalid, attempts reset) and queues the mail. */
  private void issueOtp(AppUser user) {
    String code = String.format("%06d", secureRandom.nextInt(1_000_000));
    Instant now = Instant.now();
    EmailOtp otp =
        emailOtpRepository
            .findByUserId(user.getId())
            .orElseGet(() -> EmailOtp.builder().userId(user.getId()).build());
    otp.setCodeHash(hashCode(user.getId(), code));
    otp.setExpiresAt(now.plusSeconds(OTP_TTL_SECONDS));
    otp.setAttempts(0);
    otp.setLastSentAt(now);
    otp.setConsumedAt(null);
    emailOtpRepository.save(otp);
    eventPublisher.publishEvent(
        new EmailOtpRequestedEvent(user.getEmail(), user.getFullName(), code));
  }

  /** HMAC-SHA256(pepper, userId:code), hex. The plain code is never stored. */
  private String hashCode(Long userId, String code) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(otpPepper.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
      byte[] out = mac.doFinal((userId + ":" + code).getBytes(StandardCharsets.UTF_8));
      StringBuilder sb = new StringBuilder();
      for (byte b : out) {
        sb.append(String.format("%02x", b));
      }
      return sb.toString();
    } catch (Exception e) {
      throw new IllegalStateException("HMAC-SHA256 unavailable", e);
    }
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
