package com.khmermart.modules.auth.controller;

import com.khmermart.common.api.ApiResponse;
import com.khmermart.modules.auth.dto.AuthResponse;
import com.khmermart.modules.auth.dto.ForgotPasswordRequest;
import com.khmermart.modules.auth.dto.LoginRequest;
import com.khmermart.modules.auth.dto.RegisterRequest;
import com.khmermart.modules.auth.dto.ResetPasswordRequest;
import com.khmermart.modules.auth.dto.VerifyEmailRequest;
import com.khmermart.modules.auth.service.AuthService;
import com.khmermart.modules.auth.service.AuthService.AuthResult;
import com.khmermart.security.CookieUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
@Tag(name = "Authentication", description = "Authentication and session management")
public class AuthController {

  private final AuthService authService;
  private final CookieUtil cookieUtil;

  public AuthController(AuthService authService, CookieUtil cookieUtil) {
    this.authService = authService;
    this.cookieUtil = cookieUtil;
  }

  @PostMapping("/register")
  @Operation(summary = "Register a new customer account")
  public ResponseEntity<ApiResponse<AuthResponse>> register(
      @Valid @RequestBody RegisterRequest request) {
    AuthResult result = authService.register(request);
    return ResponseEntity.ok()
        .header(HttpHeaders.SET_COOKIE, result.cookie().toString())
        .body(ApiResponse.ok(result.authResponse()));
  }

  @PostMapping("/login")
  @Operation(summary = "Authenticate user with email and password")
  public ResponseEntity<ApiResponse<AuthResponse>> login(
      @Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
    String clientIp = getClientIp(httpRequest);
    AuthResult result = authService.login(request, clientIp);
    return ResponseEntity.ok()
        .header(HttpHeaders.SET_COOKIE, result.cookie().toString())
        .body(ApiResponse.ok(result.authResponse()));
  }

  @PostMapping("/refresh")
  @Operation(summary = "Rotate refresh token and issue new access token")
  public ResponseEntity<ApiResponse<AuthResponse>> refresh(
      @CookieValue(name = CookieUtil.REFRESH_TOKEN_COOKIE_NAME, required = false)
          String refreshToken,
      HttpServletRequest httpRequest) {
    String token =
        refreshToken != null
            ? refreshToken
            : cookieUtil.getRefreshTokenFromCookie(httpRequest).orElse(null);
    try {
      AuthResult result = authService.refresh(token);
      return ResponseEntity.ok()
          .header(HttpHeaders.SET_COOKIE, result.cookie().toString())
          .body(ApiResponse.ok(result.authResponse()));
    } catch (com.khmermart.common.api.ApiException ex) {
      ResponseCookie deleteCookie = cookieUtil.deleteRefreshTokenCookie();
      return ResponseEntity.status(ex.getErrorCode().getHttpStatus())
          .header(HttpHeaders.SET_COOKIE, deleteCookie.toString())
          .body(ApiResponse.error(ex.getErrorCode(), ex.getMessage(), ex.getDetails()));
    }
  }

  @PostMapping("/logout")
  @Operation(summary = "Revoke session and clear refresh token cookie")
  public ResponseEntity<ApiResponse<Void>> logout(
      @CookieValue(name = CookieUtil.REFRESH_TOKEN_COOKIE_NAME, required = false)
          String refreshToken,
      HttpServletRequest httpRequest) {
    String token =
        refreshToken != null
            ? refreshToken
            : cookieUtil.getRefreshTokenFromCookie(httpRequest).orElse(null);
    ResponseCookie deleteCookie = authService.logout(token);
    return ResponseEntity.ok()
        .header(HttpHeaders.SET_COOKIE, deleteCookie.toString())
        .body(ApiResponse.ok());
  }

  @PostMapping("/forgot-password")
  @Operation(summary = "Request password reset email")
  public ApiResponse<Void> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
    authService.forgotPassword(request);
    return ApiResponse.ok();
  }

  @PostMapping("/reset-password")
  @Operation(summary = "Reset password using reset token")
  public ApiResponse<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
    authService.resetPassword(request);
    return ApiResponse.ok();
  }

  @PostMapping("/verify-email")
  @Operation(summary = "Verify account email address")
  public ApiResponse<Void> verifyEmail(@Valid @RequestBody VerifyEmailRequest request) {
    authService.verifyEmail(request);
    return ApiResponse.ok();
  }

  private String getClientIp(HttpServletRequest request) {
    String xfHeader = request.getHeader("X-Forwarded-For");
    if (xfHeader == null || xfHeader.isBlank()) {
      return request.getRemoteAddr();
    }
    return xfHeader.split(",")[0].trim();
  }
}
