package com.khmermart.modules.admin.controller;

import com.khmermart.common.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/dashboard")
@Tag(name = "Admin Dashboard", description = "Administrative dashboard metrics")
public class AdminDashboardController {

  @GetMapping
  @Operation(summary = "Get admin dashboard metrics")
  public ApiResponse<Map<String, Object>> getDashboard(
      @RequestParam(name = "range", defaultValue = "today") String range) {
    return ApiResponse.ok(Map.of("range", range, "status", "active"));
  }
}
