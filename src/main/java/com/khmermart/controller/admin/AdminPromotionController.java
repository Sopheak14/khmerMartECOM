package com.khmermart.controller.admin;

import com.khmermart.aspect.Audited;
import com.khmermart.common.response.SuccessResponse;
import com.khmermart.model.request.AdminPromotionRequest;
import com.khmermart.model.response.AdminPromotionDto;
import com.khmermart.security.CurrentUser;
import com.khmermart.security.UserPrincipal;
import com.khmermart.service.AdminMarketingService;
import com.khmermart.util.ApiResponseUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/promotions")
@Tag(name = "Admin Promotions", description = "Marketing promotions and campaign management")
public class AdminPromotionController {

  private final AdminMarketingService adminMarketingService;

  public AdminPromotionController(AdminMarketingService adminMarketingService) {
    this.adminMarketingService = adminMarketingService;
  }

  @GetMapping
  @PreAuthorize("hasAnyRole('STAFF', 'ADMIN')")
  @Operation(summary = "List all promotions")
  public SuccessResponse<List<AdminPromotionDto>> getPromotions() {
    List<AdminPromotionDto> list = adminMarketingService.getPromotions();
    return ApiResponseUtil.success(list);
  }

  @GetMapping("/{id}")
  @PreAuthorize("hasAnyRole('STAFF', 'ADMIN')")
  @Operation(summary = "Get promotion detail")
  public SuccessResponse<AdminPromotionDto> getPromotion(@PathVariable Long id) {
    AdminPromotionDto dto = adminMarketingService.getPromotion(id);
    return ApiResponseUtil.success(dto);
  }

  @PostMapping
  @PreAuthorize("hasAnyRole('STAFF', 'ADMIN')")
  @Audited(action = "CREATE_PROMOTION", entity = "PROMOTION")
  @Operation(summary = "Create marketing promotion with date and percent validation")
  public SuccessResponse<AdminPromotionDto> createPromotion(
      @Valid @RequestBody AdminPromotionRequest request,
      @CurrentUser UserPrincipal principal,
      HttpServletRequest servletRequest) {
    String ip = resolveIp(servletRequest);
    Long adminId = principal != null ? principal.id() : null;
    AdminPromotionDto dto = adminMarketingService.createPromotion(request, adminId, ip);
    return ApiResponseUtil.success(dto);
  }

  @PutMapping("/{id}")
  @PreAuthorize("hasAnyRole('STAFF', 'ADMIN')")
  @Audited(action = "UPDATE_PROMOTION", entity = "PROMOTION")
  @Operation(summary = "Update promotion")
  public SuccessResponse<AdminPromotionDto> updatePromotion(
      @PathVariable Long id,
      @Valid @RequestBody AdminPromotionRequest request,
      @CurrentUser UserPrincipal principal,
      HttpServletRequest servletRequest) {
    String ip = resolveIp(servletRequest);
    Long adminId = principal != null ? principal.id() : null;
    AdminPromotionDto dto = adminMarketingService.updatePromotion(id, request, adminId, ip);
    return ApiResponseUtil.success(dto);
  }

  @DeleteMapping("/{id}")
  @PreAuthorize("hasAnyRole('STAFF', 'ADMIN')")
  @Audited(action = "DELETE_PROMOTION", entity = "PROMOTION")
  @Operation(summary = "Delete promotion")
  public SuccessResponse<Map<String, Object>> deletePromotion(
      @PathVariable Long id,
      @CurrentUser UserPrincipal principal,
      HttpServletRequest servletRequest) {
    String ip = resolveIp(servletRequest);
    Long adminId = principal != null ? principal.id() : null;
    adminMarketingService.deletePromotion(id, adminId, ip);
    return ApiResponseUtil.success(Map.of());
  }

  private String resolveIp(HttpServletRequest request) {
    String xff = request.getHeader("X-Forwarded-For");
    if (xff != null && !xff.isBlank()) {
      return xff.split(",")[0].trim();
    }
    return request.getRemoteAddr();
  }
}
