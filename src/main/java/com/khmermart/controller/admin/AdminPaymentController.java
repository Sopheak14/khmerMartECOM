package com.khmermart.controller.admin;

import com.khmermart.aspect.Audited;
import com.khmermart.common.response.PaginationResponse;
import com.khmermart.common.response.SuccessResponse;
import com.khmermart.model.enumerate.PaymentMethod;
import com.khmermart.model.enumerate.PaymentStatus;
import com.khmermart.model.request.CompleteRefundRequest;
import com.khmermart.model.request.RefundRequest;
import com.khmermart.model.response.AdminPaymentDto;
import com.khmermart.model.response.OrderDto;
import com.khmermart.model.response.PaymentCheckResultDto;
import com.khmermart.model.response.ReconciliationReportDto;
import com.khmermart.security.CurrentUser;
import com.khmermart.security.UserPrincipal;
import com.khmermart.service.AdminPaymentService;
import com.khmermart.service.BankTransferService;
import com.khmermart.util.ApiResponseUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/payments")
@Tag(name = "Admin Payments", description = "Admin payment operations and reconciliation")
public class AdminPaymentController {

  private final AdminPaymentService adminPaymentService;
  private final BankTransferService bankTransferService;

  public AdminPaymentController(
      AdminPaymentService adminPaymentService, BankTransferService bankTransferService) {
    this.adminPaymentService = adminPaymentService;
    this.bankTransferService = bankTransferService;
  }

  @GetMapping
  @PreAuthorize("hasRole('ADMIN')")
  @Operation(summary = "Search and list payments with filters and pagination")
  public SuccessResponse<List<AdminPaymentDto>> getPayments(
      @RequestParam(required = false) PaymentStatus status,
      @RequestParam(required = false) PaymentMethod method,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          Instant from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          Instant to,
      @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
          Pageable pageable) {
    Page<AdminPaymentDto> page =
        adminPaymentService.getPayments(status, method, from, to, pageable);
    PaginationResponse meta =
        new PaginationResponse(
            page.getNumber() + 1, page.getSize(), page.getTotalElements(), page.getTotalPages());
    return ApiResponseUtil.success(page.getContent(), meta);
  }

  @GetMapping("/{id}")
  @PreAuthorize("hasRole('ADMIN')")
  @Operation(summary = "Get payment detail with raw gateway response")
  public SuccessResponse<AdminPaymentDto> getPaymentDetail(@PathVariable Long id) {
    AdminPaymentDto dto = adminPaymentService.getPaymentDetail(id);
    return ApiResponseUtil.success(dto);
  }

  @PostMapping("/{id}/refund")
  @PreAuthorize("hasRole('ADMIN')")
  @Audited(action = "REFUND_PAYMENT", entity = "PAYMENT")
  @Operation(summary = "Process full or partial refund for a payment")
  public SuccessResponse<AdminPaymentDto> refundPayment(
      @PathVariable Long id,
      @Valid @RequestBody(required = false) RefundRequest request,
      @CurrentUser UserPrincipal principal,
      HttpServletRequest servletRequest) {
    String ip = resolveIp(servletRequest);
    Long adminId = principal != null ? principal.id() : null;
    AdminPaymentDto dto = adminPaymentService.refundPayment(id, request, adminId, ip);
    return ApiResponseUtil.success(dto);
  }

  @PostMapping("/{id}/refund/complete")
  @PreAuthorize("hasRole('ADMIN')")
  @Operation(
      summary = "Record that a pending refund was transferred manually (bank reference required)",
      description =
          "Only from REFUND_PENDING (409 INVALID_REFUND_STATE otherwise). Bakong has no refund API:"
              + " a person sends the money from the shop's bank app, then records the reference here.")
  public SuccessResponse<AdminPaymentDto> completeRefund(
      @PathVariable Long id,
      @Valid @RequestBody CompleteRefundRequest request,
      @CurrentUser UserPrincipal principal,
      HttpServletRequest servletRequest) {
    return ApiResponseUtil.success(
        adminPaymentService.completeRefund(
            id, request, principal != null ? principal.id() : null, resolveIp(servletRequest)));
  }

  @PostMapping("/{id}/recheck")
  @PreAuthorize("hasRole('ADMIN')")
  @Operation(
      summary = "Re-check a KHQR payment against Bakong now",
      description =
          "Runs the same single check as the background poller. Confirms the payment if Bakong"
              + " reports it paid (recorded as a late payment if it had already expired).")
  public SuccessResponse<PaymentCheckResultDto> recheckPayment(
      @PathVariable Long id,
      @CurrentUser UserPrincipal principal,
      HttpServletRequest servletRequest) {
    Long adminId = principal != null ? principal.id() : null;
    return ApiResponseUtil.success(
        adminPaymentService.recheckPayment(id, adminId, resolveIp(servletRequest)));
  }

  @PostMapping("/{id}/approve-bank-transfer")
  @PreAuthorize("hasRole('ADMIN')")
  @Audited(action = "APPROVE_BANK_TRANSFER", entity = "PAYMENT")
  @Operation(summary = "Approve bank transfer slip and confirm order payment")
  public SuccessResponse<OrderDto> approveBankTransfer(
      @CurrentUser UserPrincipal principal, @PathVariable Long id) {
    OrderDto orderDto = bankTransferService.approveBankTransfer(id, principal.id());
    return ApiResponseUtil.success(orderDto);
  }

  @GetMapping(value = "/export.csv", produces = "text/csv")
  @PreAuthorize("hasRole('ADMIN')")
  @Operation(summary = "Export payments as CSV")
  public ResponseEntity<byte[]> exportPaymentsCsv() {
    byte[] csvData = adminPaymentService.exportPaymentsCsv();
    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"payments.csv\"")
        .contentType(MediaType.parseMediaType("text/csv"))
        .body(csvData);
  }

  @GetMapping("/reconciliation")
  @PreAuthorize("hasRole('ADMIN')")
  @Operation(summary = "Get payment and order reconciliation report")
  public SuccessResponse<ReconciliationReportDto> getReconciliation() {
    ReconciliationReportDto report = adminPaymentService.getReconciliationReport();
    return ApiResponseUtil.success(report);
  }

  private String resolveIp(HttpServletRequest request) {
    String xff = request.getHeader("X-Forwarded-For");
    if (xff != null && !xff.isBlank()) {
      return xff.split(",")[0].trim();
    }
    return request.getRemoteAddr();
  }
}
