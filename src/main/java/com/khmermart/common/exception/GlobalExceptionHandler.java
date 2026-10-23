package com.khmermart.common.exception;

import com.khmermart.common.constant.MessageConstant;
import com.khmermart.common.response.ErrorResponse;
import com.khmermart.util.ApiResponseUtil;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

  @ExceptionHandler(CustomException.class)
  public ResponseEntity<ErrorResponse> handleCustomException(CustomException ex) {
    return ResponseEntity.status(ex.getHttpStatus())
        .body(ApiResponseUtil.error(ex.getErrorCode(), ex.getMessage(), ex.getDetails()));
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ErrorResponse> handleMethodArgumentNotValid(
      MethodArgumentNotValidException ex) {
    Map<String, String> details = new HashMap<>();
    for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
      details.put(fieldError.getField(), fieldError.getDefaultMessage());
    }
    return ResponseEntity.status(ErrorCode.VALIDATION_ERROR.getHttpStatus())
        .body(
            ApiResponseUtil.error(
                ErrorCode.VALIDATION_ERROR, MessageConstant.VALIDATION_FAILED, details));
  }

  @ExceptionHandler(ConstraintViolationException.class)
  public ResponseEntity<ErrorResponse> handleConstraintViolation(ConstraintViolationException ex) {
    Map<String, String> details = new HashMap<>();
    for (ConstraintViolation<?> violation : ex.getConstraintViolations()) {
      details.put(violation.getPropertyPath().toString(), violation.getMessage());
    }
    return ResponseEntity.status(ErrorCode.VALIDATION_ERROR.getHttpStatus())
        .body(
            ApiResponseUtil.error(
                ErrorCode.VALIDATION_ERROR, MessageConstant.VALIDATION_FAILED, details));
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<ErrorResponse> handleNotReadable(HttpMessageNotReadableException ex) {
    return ResponseEntity.status(ErrorCode.VALIDATION_ERROR.getHttpStatus())
        .body(ApiResponseUtil.error(ErrorCode.VALIDATION_ERROR, MessageConstant.MALFORMED_JSON));
  }

  @ExceptionHandler(MaxUploadSizeExceededException.class)
  public ResponseEntity<ErrorResponse> handleMaxUploadSize(MaxUploadSizeExceededException ex) {
    return ResponseEntity.status(ErrorCode.VALIDATION_ERROR.getHttpStatus())
        .body(
            ApiResponseUtil.error(ErrorCode.VALIDATION_ERROR, MessageConstant.FILE_SIZE_EXCEEDED));
  }

  @ExceptionHandler(NoResourceFoundException.class)
  public ResponseEntity<ErrorResponse> handleNoResourceFound(NoResourceFoundException ex) {
    return ResponseEntity.status(ErrorCode.NOT_FOUND.getHttpStatus())
        .body(
            ApiResponseUtil.error(
                ErrorCode.NOT_FOUND, "Resource not found: " + ex.getResourcePath()));
  }

  @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
  public ResponseEntity<ErrorResponse> handleOptimisticLocking(
      ObjectOptimisticLockingFailureException ex) {
    return ResponseEntity.status(ErrorCode.CONFLICT.getHttpStatus())
        .body(ApiResponseUtil.error(ErrorCode.CONFLICT, MessageConstant.CONFLICT));
  }

  @ExceptionHandler(DataIntegrityViolationException.class)
  public ResponseEntity<ErrorResponse> handleDataIntegrity(DataIntegrityViolationException ex) {
    log.warn("Database integrity violation: {}", ex.getMessage());
    return ResponseEntity.status(ErrorCode.CONFLICT.getHttpStatus())
        .body(
            ApiResponseUtil.error(ErrorCode.CONFLICT, "Database constraint or integrity conflict"));
  }

  @ExceptionHandler(AccessDeniedException.class)
  public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException ex) {
    return ResponseEntity.status(ErrorCode.FORBIDDEN.getHttpStatus())
        .body(ApiResponseUtil.error(ErrorCode.FORBIDDEN, MessageConstant.ACCESS_DENIED));
  }

  @ExceptionHandler(AuthenticationException.class)
  public ResponseEntity<ErrorResponse> handleAuthentication(AuthenticationException ex) {
    return ResponseEntity.status(ErrorCode.UNAUTHORIZED.getHttpStatus())
        .body(ApiResponseUtil.error(ErrorCode.UNAUTHORIZED, MessageConstant.AUTH_REQUIRED));
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ErrorResponse> handleGeneric(Exception ex) {
    log.error("Unhandled exception: ", ex);
    return ResponseEntity.status(ErrorCode.INTERNAL_ERROR.getHttpStatus())
        .body(ApiResponseUtil.error(ErrorCode.INTERNAL_ERROR, "An unexpected error occurred"));
  }
}
