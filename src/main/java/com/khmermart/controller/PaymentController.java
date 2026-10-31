package com.khmermart.controller;

import com.khmermart.common.exception.CustomException;
import com.khmermart.common.exception.ErrorCode;
import com.khmermart.common.response.SuccessResponse;
import com.khmermart.model.enumerate.Roles;
import com.khmermart.model.response.KhqrPaymentDto;
import com.khmermart.model.response.OrderDto;
import com.khmermart.model.response.PaymentStatusDto;
import com.khmermart.security.CurrentUser;
import com.khmermart.security.JwtService;
import com.khmermart.security.UserPrincipal;
import com.khmermart.service.BankTransferService;
import com.khmermart.service.KhqrService;
import com.khmermart.service.PaymentSseRegistry;
import com.khmermart.util.ApiResponseUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/payments")
@Tag(name = "Payment", description = "Payment operations: KHQR, Bank Transfer, status stream")
public class PaymentController {

  private final KhqrService khqrService;
  private final BankTransferService bankTransferService;
  private final PaymentSseRegistry paymentSseRegistry;
  private final JwtService jwtService;

  public PaymentController(
      KhqrService khqrService,
      BankTransferService bankTransferService,
      PaymentSseRegistry paymentSseRegistry,
      JwtService jwtService) {
    this.khqrService = khqrService;
    this.bankTransferService = bankTransferService;
    this.paymentSseRegistry = paymentSseRegistry;
    this.jwtService = jwtService;
  }

  @PostMapping("/khqr/{orderNumber}")
  @Operation(summary = "Generate or retrieve active KHQR dynamic payment")
  public SuccessResponse<KhqrPaymentDto> generateKhqr(
      @CurrentUser UserPrincipal principal,
      @PathVariable String orderNumber,
      @RequestParam(required = false) String currency) {
    KhqrPaymentDto dto = khqrService.generateKhqr(principal.id(), orderNumber, currency);
    return ApiResponseUtil.success(dto);
  }

  @PostMapping("/khqr/{orderNumber}/regenerate")
  @Operation(summary = "Regenerate expired KHQR payment with fresh MD5")
  public SuccessResponse<KhqrPaymentDto> regenerateKhqr(
      @CurrentUser UserPrincipal principal, @PathVariable String orderNumber) {
    KhqrPaymentDto dto = khqrService.regenerateKhqr(principal.id(), orderNumber);
    return ApiResponseUtil.success(dto);
  }

  @GetMapping("/{paymentId}/status")
  @Operation(summary = "Get current status of a payment")
  public SuccessResponse<PaymentStatusDto> getPaymentStatus(
      @CurrentUser UserPrincipal principal, @PathVariable Long paymentId) {
    boolean isStaffOrAdmin =
        principal != null && (principal.role() == Roles.ADMIN || principal.role() == Roles.STAFF);
    PaymentStatusDto dto =
        khqrService.getPaymentStatus(
            principal != null ? principal.id() : null, paymentId, isStaffOrAdmin);
    return ApiResponseUtil.success(dto);
  }

  @GetMapping(value = "/{paymentId}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  @Operation(summary = "SSE payment status stream (owner or STAFF/ADMIN only)")
  public ResponseEntity<SseEmitter> streamPaymentStatus(
      @CurrentUser UserPrincipal principal,
      @PathVariable Long paymentId,
      @RequestParam(required = false) String token,
      HttpServletResponse response) {
    // EventSource cannot send headers, so the owner's stream token is accepted too.
    Long userId;
    boolean isStaffOrAdmin = false;
    if (principal != null) {
      userId = principal.id();
      isStaffOrAdmin = principal.role() == Roles.ADMIN || principal.role() == Roles.STAFF;
    } else {
      userId = token == null ? null : jwtService.streamTokenUserId(token, paymentId);
    }
    if (userId == null) {
      throw new CustomException(ErrorCode.UNAUTHORIZED, "Invalid or expired stream token");
    }

    // Throws 404 when the payment is not the caller's (and caller is not staff)
    PaymentStatusDto currentStatus =
        khqrService.getPaymentStatus(userId, paymentId, isStaffOrAdmin);

    response.setHeader("Cache-Control", "no-cache");
    response.setHeader("X-Accel-Buffering", "no");

    SseEmitter emitter = paymentSseRegistry.register(paymentId, currentStatus);
    return ResponseEntity.ok(emitter);
  }

  @PostMapping("/{paymentId}/simulate-paid")
  @Operation(summary = "Simulate payment completion in MOCK mode")
  public SuccessResponse<PaymentStatusDto> simulatePaid(
      @CurrentUser UserPrincipal principal, @PathVariable Long paymentId) {
    boolean isStaffOrAdmin =
        principal != null && (principal.role() == Roles.ADMIN || principal.role() == Roles.STAFF);
    PaymentStatusDto dto =
        khqrService.simulatePaid(
            principal != null ? principal.id() : null, paymentId, isStaffOrAdmin);
    return ApiResponseUtil.success(dto);
  }

  @PostMapping(
      value = "/bank-transfer/{orderNumber}/slip",
      consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @Operation(summary = "Upload bank transfer slip for verification")
  public SuccessResponse<OrderDto> uploadBankTransferSlip(
      @CurrentUser UserPrincipal principal,
      @PathVariable String orderNumber,
      @RequestParam("file") MultipartFile file) {
    OrderDto orderDto = bankTransferService.uploadSlip(principal.id(), orderNumber, file);
    return ApiResponseUtil.success(orderDto);
  }
}
