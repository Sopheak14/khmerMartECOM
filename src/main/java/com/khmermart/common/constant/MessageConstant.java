package com.khmermart.common.constant;

public final class MessageConstant {

  private MessageConstant() {}

  public static final String AUTH_REQUIRED = "Authentication required";
  public static final String ACCESS_DENIED = "Access denied";
  public static final String INVALID_CREDENTIALS = "Invalid email or password";
  public static final String EMAIL_REGISTERED = "Email is already registered";
  public static final String ACCOUNT_DISABLED = "Account is disabled";
  public static final String USER_NOT_FOUND = "User not found";
  public static final String ADDRESS_NOT_FOUND = "Address not found";
  public static final String PROVINCE_NOT_FOUND = "Province not found";
  public static final String REFRESH_TOKEN_REQUIRED = "Refresh token required";
  public static final String INVALID_REFRESH_TOKEN = "Invalid refresh token";
  public static final String TOKEN_REUSE_DETECTED = "Invalid refresh token: token reuse detected";
  public static final String TOKEN_EXPIRED = "Refresh token has expired";
  public static final String RATE_LIMITED = "Too many login attempts. Please try again later";
  public static final String INVALID_RESET_TOKEN = "Invalid or expired reset token";
  public static final String INVALID_VERIFY_TOKEN = "Invalid or expired verification token";
  public static final String CURRENT_PASSWORD_INCORRECT = "Current password is incorrect";
  public static final String VALIDATION_FAILED = "Validation failed";
  public static final String MALFORMED_JSON = "Malformed JSON request body";
  public static final String CONFLICT = "Concurrent modification conflict. Please retry";
  public static final String FILE_SIZE_EXCEEDED =
      "File size exceeds the configured maximum limit (5MB)";
}
