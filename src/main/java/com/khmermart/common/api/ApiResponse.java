package com.khmermart.common.api;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(
    boolean success, T data, PageMeta meta, Object facets, ApiError error) {

  public static <T> ApiResponse<T> ok(T data) {
    return new ApiResponse<>(true, data, null, null, null);
  }

  public static <T> ApiResponse<T> ok(T data, PageMeta meta) {
    return new ApiResponse<>(true, data, meta, null, null);
  }

  public static <T> ApiResponse<T> ok(T data, PageMeta meta, Object facets) {
    return new ApiResponse<>(true, data, meta, facets, null);
  }

  public static <T> ApiResponse<T> ok() {
    return new ApiResponse<>(true, null, null, null, null);
  }

  public static <T> ApiResponse<T> error(ErrorCode errorCode) {
    return new ApiResponse<>(
        false,
        null,
        null,
        null,
        new ApiError(errorCode.name(), errorCode.getDefaultMessage(), null));
  }

  public static <T> ApiResponse<T> error(ErrorCode errorCode, String message) {
    return new ApiResponse<>(
        false, null, null, null, new ApiError(errorCode.name(), message, null));
  }

  public static <T> ApiResponse<T> error(ErrorCode errorCode, String message, Object details) {
    return new ApiResponse<>(
        false, null, null, null, new ApiError(errorCode.name(), message, details));
  }

  public static <T> ApiResponse<T> error(String code, String message, Object details) {
    return new ApiResponse<>(false, null, null, null, new ApiError(code, message, details));
  }
}
