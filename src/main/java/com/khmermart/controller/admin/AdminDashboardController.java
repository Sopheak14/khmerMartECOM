package com.khmermart.controller.admin;

import com.khmermart.common.response.SuccessResponse;
import com.khmermart.model.response.DashboardDto;
import com.khmermart.service.DashboardService;
import com.khmermart.util.ApiResponseUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin")
@Tag(name = "Admin", description = "Backoffice administration and dashboard")
public class AdminDashboardController {

  private final DashboardService dashboardService;

  public AdminDashboardController(DashboardService dashboardService) {
    this.dashboardService = dashboardService;
  }

  @GetMapping("/dashboard")
  @PreAuthorize("hasAnyRole('STAFF', 'ADMIN')")
  @Operation(summary = "Get admin dashboard KPI metrics")
  public SuccessResponse<DashboardDto> getDashboard(
      @RequestParam(required = false, defaultValue = "30d") String range) {
    DashboardDto dto = dashboardService.getDashboard(range);
    return ApiResponseUtil.success(dto);
  }
}
