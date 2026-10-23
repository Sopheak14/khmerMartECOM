package com.khmermart.controller;

import com.khmermart.common.constant.AppConstant;
import com.khmermart.common.exception.CustomException;
import com.khmermart.common.response.ErrorResponse;
import com.khmermart.common.response.SuccessResponse;
import com.khmermart.common.response.TokenResponse;
import com.khmermart.model.request.AuthRequest;
import com.khmermart.model.request.ForgotPasswordRequest;
import com.khmermart.model.request.RegisterRequest;
import com.khmermart.model.request.ResetPasswordRequest;
import com.khmermart.model.request.VerifyEmailRequest;
import com.khmermart.security.CookieUtil;
import com.khmermart.service.AuthService;
import com.khmermart.service.AuthService.AuthResult;
import com.khmermart.util.ApiResponseUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.Map;
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
  public ResponseEntity<SuccessResponse<TokenResponse>> register(
      @Valid @RequestBody RegisterRequest request) {
    AuthResult result = authService.register(request);
    return ResponseEntity.ok()
        .header(HttpHeaders.SET_COOKIE, result.cookie().toString())
        .body(ApiResponseUtil.success(result.tokenResponse()));
  }

  @PostMapping("/login")
  @Operation(summary = "Authenticate user with email and password")
  public ResponseEntity<SuccessResponse<TokenResponse>> login(
      @Valid @RequestBody AuthRequest request, HttpServletRequest httpRequest) {
    String clientIp = getClientIp(httpRequest);
    AuthResult result = authService.login(request, clientIp);
    return ResponseEntity.ok()
        .header(HttpHeaders.SET_COOKIE, result.cookie().toString())
        .body(ApiResponseUtil.success(result.tokenResponse()));
  }

  @PostMapping("/refresh")
  @Operation(summary = "Rotate refresh token and issue new access token")
  public ResponseEntity<?> refresh(
      @CookieValue(name = AppConstant.REFRESH_TOKEN_COOKIE_NAME, required = false)
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
          .body(ApiResponseUtil.success(result.tokenResponse()));
    } catch (CustomException ex) {
      ResponseCookie deleteCookie = cookieUtil.deleteRefreshTokenCookie();
      ErrorResponse errorBody =
          ApiResponseUtil.error(ex.getErrorCode(), ex.getMessage(), ex.getDetails());
      return ResponseEntity.status(ex.getErrorCode().getHttpStatus())
          .header(HttpHeaders.SET_COOKIE, deleteCookie.toString())
          .body(errorBody);
    }
  }

  @PostMapping("/logout")
  @Operation(summary = "Revoke session and clear refresh token cookie")
  public ResponseEntity<SuccessResponse<Map<String, Object>>> logout(
      @CookieValue(name = AppConstant.REFRESH_TOKEN_COOKIE_NAME, required = false)
          String refreshToken,
      HttpServletRequest httpRequest) {
    String token =
        refreshToken != null
            ? refreshToken
            : cookieUtil.getRefreshTokenFromCookie(httpRequest).orElse(null);
    ResponseCookie deleteCookie = authService.logout(token);
    return ResponseEntity.ok()
        .header(HttpHeaders.SET_COOKIE, deleteCookie.toString())
        .body(ApiResponseUtil.success());
  }

  @PostMapping("/forgot-password")
  @Operation(summary = "Request password reset email")
  public SuccessResponse<Map<String, Object>> forgotPassword(
      @Valid @RequestBody ForgotPasswordRequest request) {
    authService.forgotPassword(request);
    return ApiResponseUtil.success();
  }

  @PostMapping("/reset-password")
  @Operation(summary = "Reset password using reset token")
  public SuccessResponse<Map<String, Object>> resetPassword(
      @Valid @RequestBody ResetPasswordRequest request) {
    authService.resetPassword(request);
    return ApiResponseUtil.success();
  }

  @PostMapping("/verify-email")
  @Operation(summary = "Verify account email address")
  public SuccessResponse<Map<String, Object>> verifyEmail(
      @Valid @RequestBody VerifyEmailRequest request) {
    authService.verifyEmail(request);
    return ApiResponseUtil.success();
  }

  private String getClientIp(HttpServletRequest request) {
    String xfHeader = request.getHeader("X-Forwarded-For");
    if (xfHeader == null || xfHeader.isBlank()) {
      return request.getRemoteAddr();
    }
    return xfHeader.split(",")[0].trim();
  }
}
