package com.khmermart.util;

import com.khmermart.common.exception.ErrorCode;
import com.khmermart.common.response.ErrorResponse;
import com.khmermart.common.response.PaginationResponse;
import com.khmermart.common.response.SuccessResponse;

public final class ApiResponseUtil {

  private ApiResponseUtil() {}

  public static <T> SuccessResponse<T> success(T data) {
    return new SuccessResponse<>(data);
  }

  public static <T> SuccessResponse<T> success(T data, PaginationResponse meta) {
    return new SuccessResponse<>(data, meta);
  }

  public static <T> SuccessResponse<T> success(T data, PaginationResponse meta, Object facets) {
    return new SuccessResponse<>(data, meta, facets);
  }

  public static SuccessResponse<Void> success() {
    return new SuccessResponse<>(null);
  }

  public static ErrorResponse error(ErrorCode errorCode) {
    return new ErrorResponse(errorCode.name(), errorCode.getDefaultMessage(), null);
  }

  public static ErrorResponse error(ErrorCode errorCode, String message) {
    return new ErrorResponse(errorCode.name(), message, null);
  }

  public static ErrorResponse error(ErrorCode errorCode, String message, Object details) {
    return new ErrorResponse(errorCode.name(), message, details);
  }

  public static ErrorResponse error(String code, String message, Object details) {
    return new ErrorResponse(code, message, details);
  }
}
