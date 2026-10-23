package com.khmermart.controller.admin;

import com.khmermart.common.response.SuccessResponse;
import com.khmermart.util.ApiResponseUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin")
@Tag(name = "Admin", description = "Backoffice administration")
public class AdminDashboardController {

  @GetMapping("/dashboard")
  @PreAuthorize("hasRole('ADMIN')")
  @Operation(summary = "Get admin dashboard KPI metrics")
  public SuccessResponse<Map<String, Object>> getDashboard() {
    return ApiResponseUtil.success(
        Map.of(
            "revenueTodayCents", 0,
            "ordersToday", 0,
            "ordersPending", 0));
  }
}
