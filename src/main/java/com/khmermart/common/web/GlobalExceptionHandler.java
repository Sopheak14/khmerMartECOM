package com.khmermart.common.web;

import com.khmermart.common.api.ApiException;
import com.khmermart.common.api.ApiResponse;
import com.khmermart.common.api.ErrorCode;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  @ExceptionHandler(ApiException.class)
  public ResponseEntity<ApiResponse<Void>> handleApiException(ApiException ex) {
    ErrorCode code = ex.getErrorCode();
    return ResponseEntity.status(code.getHttpStatus())
        .body(ApiResponse.error(code, ex.getMessage(), ex.getDetails()));
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ApiResponse<Void>> handleMethodArgumentNotValid(
      MethodArgumentNotValidException ex) {
    Map<String, String> details = new HashMap<>();
    for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
      details.put(fieldError.getField(), fieldError.getDefaultMessage());
    }
    return ResponseEntity.status(ErrorCode.VALIDATION_ERROR.getHttpStatus())
        .body(ApiResponse.error(ErrorCode.VALIDATION_ERROR, "Validation failed", details));
  }

  @ExceptionHandler(ConstraintViolationException.class)
  public ResponseEntity<ApiResponse<Void>> handleConstraintViolation(
      ConstraintViolationException ex) {
    Map<String, String> details = new HashMap<>();
    for (ConstraintViolation<?> violation : ex.getConstraintViolations()) {
      details.put(violation.getPropertyPath().toString(), violation.getMessage());
    }
    return ResponseEntity.status(ErrorCode.VALIDATION_ERROR.getHttpStatus())
        .body(ApiResponse.error(ErrorCode.VALIDATION_ERROR, "Constraint violation", details));
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<ApiResponse<Void>> handleNotReadable(HttpMessageNotReadableException ex) {
    return ResponseEntity.status(ErrorCode.VALIDATION_ERROR.getHttpStatus())
        .body(ApiResponse.error(ErrorCode.VALIDATION_ERROR, "Malformed JSON request body"));
  }

  @ExceptionHandler(MaxUploadSizeExceededException.class)
  public ResponseEntity<ApiResponse<Void>> handleMaxUploadSize(MaxUploadSizeExceededException ex) {
    return ResponseEntity.status(ErrorCode.VALIDATION_ERROR.getHttpStatus())
        .body(
            ApiResponse.error(
                ErrorCode.VALIDATION_ERROR,
                "File size exceeds the configured maximum limit (5MB)"));
  }

  @ExceptionHandler(NoResourceFoundException.class)
  public ResponseEntity<ApiResponse<Void>> handleNoResourceFound(NoResourceFoundException ex) {
    return ResponseEntity.status(ErrorCode.NOT_FOUND.getHttpStatus())
        .body(
            ApiResponse.error(ErrorCode.NOT_FOUND, "Resource not found: " + ex.getResourcePath()));
  }

  @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
  public ResponseEntity<ApiResponse<Void>> handleOptimisticLocking(
      ObjectOptimisticLockingFailureException ex) {
    return ResponseEntity.status(ErrorCode.CONFLICT.getHttpStatus())
        .body(
            ApiResponse.error(
                ErrorCode.CONFLICT, "Concurrent modification conflict. Please retry"));
  }

  @ExceptionHandler(DataIntegrityViolationException.class)
  public ResponseEntity<ApiResponse<Void>> handleDataIntegrity(DataIntegrityViolationException ex) {
    log.warn("Database integrity violation: {}", ex.getMessage());
    return ResponseEntity.status(ErrorCode.CONFLICT.getHttpStatus())
        .body(ApiResponse.error(ErrorCode.CONFLICT, "Database constraint or integrity conflict"));
  }

  @ExceptionHandler(AccessDeniedException.class)
  public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException ex) {
    return ResponseEntity.status(ErrorCode.FORBIDDEN.getHttpStatus())
        .body(ApiResponse.error(ErrorCode.FORBIDDEN, "Access denied"));
  }

  @ExceptionHandler(AuthenticationException.class)
  public ResponseEntity<ApiResponse<Void>> handleAuthentication(AuthenticationException ex) {
    return ResponseEntity.status(ErrorCode.UNAUTHORIZED.getHttpStatus())
        .body(ApiResponse.error(ErrorCode.UNAUTHORIZED, "Authentication required"));
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ApiResponse<Void>> handleGenericException(Exception ex) {
    String requestId = MDC.get(RequestIdFilter.MDC_REQUEST_ID_KEY);
    log.error("Unhandled server error [requestId={}]: {}", requestId, ex.getMessage(), ex);
    return ResponseEntity.status(ErrorCode.INTERNAL_ERROR.getHttpStatus())
        .body(ApiResponse.error(ErrorCode.INTERNAL_ERROR, "An internal server error occurred"));
  }
}
