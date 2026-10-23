package com.khmermart.controller.admin;

import com.khmermart.common.response.SuccessResponse;
import com.khmermart.model.response.OrderDto;
import com.khmermart.security.CurrentUser;
import com.khmermart.security.UserPrincipal;
import com.khmermart.service.BankTransferService;
import com.khmermart.util.ApiResponseUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/payments")
@Tag(name = "Admin Payments", description = "Admin payment operations")
public class AdminPaymentController {

  private final BankTransferService bankTransferService;

  public AdminPaymentController(BankTransferService bankTransferService) {
    this.bankTransferService = bankTransferService;
  }

  @PostMapping("/{id}/approve-bank-transfer")
  @PreAuthorize("hasRole('ADMIN')")
  @Operation(summary = "Approve bank transfer slip and confirm order payment")
  public SuccessResponse<OrderDto> approveBankTransfer(
      @CurrentUser UserPrincipal principal, @PathVariable Long id) {
    OrderDto orderDto = bankTransferService.approveBankTransfer(id, principal.id());
    return ApiResponseUtil.success(orderDto);
  }
}
