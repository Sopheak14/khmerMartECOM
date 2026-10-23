package com.khmermart.common.exception;

import org.springframework.http.HttpStatus;

public class CustomException extends RuntimeException {

  private final ErrorCode errorCode;
  private final Object details;
  private final HttpStatus httpStatusOverride;

  public CustomException(ErrorCode errorCode) {
    super(errorCode.getDefaultMessage());
    this.errorCode = errorCode;
    this.details = null;
    this.httpStatusOverride = null;
  }

  public CustomException(ErrorCode errorCode, String message) {
    super(message);
    this.errorCode = errorCode;
    this.details = null;
    this.httpStatusOverride = null;
  }

  public CustomException(ErrorCode errorCode, String message, Object details) {
    super(message);
    this.errorCode = errorCode;
    this.details = details;
    this.httpStatusOverride = null;
  }

  public CustomException(ErrorCode errorCode, String message, HttpStatus httpStatusOverride) {
    super(message);
    this.errorCode = errorCode;
    this.details = null;
    this.httpStatusOverride = httpStatusOverride;
  }

  public CustomException(
      ErrorCode errorCode, String message, Object details, HttpStatus httpStatusOverride) {
    super(message);
    this.errorCode = errorCode;
    this.details = details;
    this.httpStatusOverride = httpStatusOverride;
  }

  public ErrorCode getErrorCode() {
    return errorCode;
  }

  public Object getDetails() {
    return details;
  }

  public HttpStatus getHttpStatus() {
    return httpStatusOverride != null ? httpStatusOverride : errorCode.getHttpStatus();
  }
}
