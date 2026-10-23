package com.khmermart.common.exception;

public class CustomException extends RuntimeException {

  private final ErrorCode errorCode;
  private final Object details;

  public CustomException(ErrorCode errorCode) {
    super(errorCode.getDefaultMessage());
    this.errorCode = errorCode;
    this.details = null;
  }

  public CustomException(ErrorCode errorCode, String message) {
    super(message);
    this.errorCode = errorCode;
    this.details = null;
  }

  public CustomException(ErrorCode errorCode, String message, Object details) {
    super(message);
    this.errorCode = errorCode;
    this.details = details;
  }

  public ErrorCode getErrorCode() {
    return errorCode;
  }

  public Object getDetails() {
    return details;
  }
}
