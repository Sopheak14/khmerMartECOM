package com.khmermart.controller;

import com.khmermart.common.exception.CustomException;
import com.khmermart.common.exception.ErrorCode;
import com.khmermart.common.response.SuccessResponse;
import com.khmermart.model.request.CheckoutRequest;
import com.khmermart.model.response.CheckoutPreviewDto;
import com.khmermart.model.response.CheckoutResponseDto;
import com.khmermart.security.CurrentUserService;
import com.khmermart.service.CheckoutService;
import com.khmermart.util.ApiResponseUtil;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/checkout")
public class CheckoutController {

  private final CheckoutService checkoutService;
  private final CurrentUserService currentUserService;

  public CheckoutController(
      CheckoutService checkoutService, CurrentUserService currentUserService) {
    this.checkoutService = checkoutService;
    this.currentUserService = currentUserService;
  }

  @PostMapping("/preview")
  public ResponseEntity<SuccessResponse<CheckoutPreviewDto>> preview(
      @Valid @RequestBody CheckoutRequest request) {
    Long userId =
        currentUserService
            .getCurrentUserId()
            .orElseThrow(
                () -> new CustomException(ErrorCode.UNAUTHORIZED, "Authentication required"));

    return ResponseEntity.ok(ApiResponseUtil.success(checkoutService.preview(userId, request)));
  }

  @PostMapping
  public ResponseEntity<SuccessResponse<CheckoutResponseDto>> checkout(
      @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
      @Valid @RequestBody CheckoutRequest request) {
    Long userId =
        currentUserService
            .getCurrentUserId()
            .orElseThrow(
                () -> new CustomException(ErrorCode.UNAUTHORIZED, "Authentication required"));

    return ResponseEntity.ok(
        ApiResponseUtil.success(checkoutService.checkout(userId, idempotencyKey, request)));
  }
}
