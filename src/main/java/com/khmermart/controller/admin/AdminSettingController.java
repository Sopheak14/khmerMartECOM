package com.khmermart.controller.admin;

import com.khmermart.aspect.Audited;
import com.khmermart.common.response.SuccessResponse;
import com.khmermart.model.request.AdminSettingsUpdateRequest;
import com.khmermart.model.response.AdminSettingsDto;
import com.khmermart.security.CurrentUser;
import com.khmermart.security.UserPrincipal;
import com.khmermart.service.SettingService;
import com.khmermart.util.ApiResponseUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/settings")
@Tag(name = "Admin Settings", description = "Global store configuration and exchange rates")
public class AdminSettingController {

  private final SettingService settingService;

  public AdminSettingController(SettingService settingService) {
    this.settingService = settingService;
  }

  @GetMapping
  @PreAuthorize("hasRole('ADMIN')")
  @Operation(summary = "Get global store settings")
  public SuccessResponse<AdminSettingsDto> getSettings() {
    AdminSettingsDto dto = settingService.getAdminSettings();
    return ApiResponseUtil.success(dto);
  }

  @PutMapping
  @PreAuthorize("hasRole('ADMIN')")
  @Audited(action = "UPDATE_SETTINGS", entity = "SETTINGS")
  @Operation(
      summary = "Update store settings (switching to LIVE mode without BAKONG_TOKEN returns 422)")
  public SuccessResponse<AdminSettingsDto> updateSettings(
      @RequestBody AdminSettingsUpdateRequest request,
      @CurrentUser UserPrincipal principal,
      HttpServletRequest servletRequest) {
    String ip = resolveIp(servletRequest);
    Long adminId = principal != null ? principal.id() : null;
    AdminSettingsDto dto = settingService.updateAdminSettings(request, adminId, ip);
    return ApiResponseUtil.success(dto);
  }

  private String resolveIp(HttpServletRequest request) {
    String xff = request.getHeader("X-Forwarded-For");
    if (xff != null && !xff.isBlank()) {
      return xff.split(",")[0].trim();
    }
    return request.getRemoteAddr();
  }
}
