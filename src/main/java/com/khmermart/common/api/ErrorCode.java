package com.khmermart.common.api;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
  VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "Request validation failed"),
  UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "Authentication required"),
  TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "Token has expired"),
  FORBIDDEN(HttpStatus.FORBIDDEN, "Access denied"),
  NOT_FOUND(HttpStatus.NOT_FOUND, "Resource not found"),
  CONFLICT(HttpStatus.CONFLICT, "Resource conflict"),
  OUT_OF_STOCK(HttpStatus.CONFLICT, "Item is out of stock"),
  PRICE_CHANGED(HttpStatus.CONFLICT, "Product price has changed"),
  COUPON_INVALID(HttpStatus.UNPROCESSABLE_ENTITY, "Coupon is invalid"),
  COUPON_EXPIRED(HttpStatus.UNPROCESSABLE_ENTITY, "Coupon has expired"),
  COUPON_MIN_ORDER(
      HttpStatus.UNPROCESSABLE_ENTITY, "Order does not meet minimum amount for this coupon"),
  INVALID_STATUS_TRANSITION(HttpStatus.UNPROCESSABLE_ENTITY, "Invalid order status transition"),
  PAYMENT_EXPIRED(HttpStatus.GONE, "Payment has expired"),
  KHQR_GENERATION_FAILED(HttpStatus.BAD_GATEWAY, "Failed to generate KHQR"),
  BAKONG_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "Bakong service is currently unavailable"),
  RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "Too many requests. Please try again later"),
  INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "An internal server error occurred");

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
