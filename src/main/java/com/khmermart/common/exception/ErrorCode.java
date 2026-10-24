package com.khmermart.common.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
  VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "Validation failed"),
  UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "Authentication required"),
  USER_DEACTIVATED(HttpStatus.UNAUTHORIZED, "User account is deactivated"),
  TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "Token has expired"),
  FORBIDDEN(HttpStatus.FORBIDDEN, "Access denied"),
  NOT_FOUND(HttpStatus.NOT_FOUND, "Resource not found"),
  CONFLICT(HttpStatus.CONFLICT, "Resource conflict"),
  OUT_OF_STOCK(HttpStatus.CONFLICT, "Item is out of stock"),
  PRICE_CHANGED(HttpStatus.CONFLICT, "Product price has changed"),
  COUPON_INVALID(HttpStatus.UNPROCESSABLE_ENTITY, "Coupon code is invalid"),
  COUPON_EXPIRED(HttpStatus.UNPROCESSABLE_ENTITY, "Coupon has expired"),
  COUPON_MIN_ORDER(HttpStatus.UNPROCESSABLE_ENTITY, "Minimum order subtotal not met for coupon"),
  INVALID_STATUS_TRANSITION(HttpStatus.UNPROCESSABLE_ENTITY, "Invalid status transition"),
  PAYMENT_EXPIRED(HttpStatus.GONE, "Payment has expired"),
  KHQR_GENERATION_FAILED(HttpStatus.BAD_GATEWAY, "Failed to generate KHQR code"),
  BAKONG_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "Bakong service is temporarily unavailable"),
  RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "Too many requests"),
  INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error");

  private final HttpStatus httpStatus;
  private final String defaultMessage;

  ErrorCode(HttpStatus httpStatus, String defaultMessage) {
    this.httpStatus = httpStatus;
    this.defaultMessage = defaultMessage;
  }

  public HttpStatus getHttpStatus() {
    return httpStatus;
  }

  public String getDefaultMessage() {
    return defaultMessage;
  }
}
