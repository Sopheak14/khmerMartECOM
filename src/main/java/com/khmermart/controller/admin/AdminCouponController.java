package com.khmermart.controller.admin;

import com.khmermart.aspect.Audited;
import com.khmermart.common.response.SuccessResponse;
import com.khmermart.model.request.AdminCouponRequest;
import com.khmermart.model.response.AdminCouponDto;
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
@RequestMapping("/admin/coupons")
@Tag(name = "Admin Coupons", description = "Coupon creation and management")
public class AdminCouponController {

  private final AdminMarketingService adminMarketingService;

  public AdminCouponController(AdminMarketingService adminMarketingService) {
    this.adminMarketingService = adminMarketingService;
  }

  @GetMapping
  @PreAuthorize("hasAnyRole('STAFF', 'ADMIN')")
  @Operation(summary = "List all coupons")
  public SuccessResponse<List<AdminCouponDto>> getCoupons() {
    List<AdminCouponDto> list = adminMarketingService.getCoupons();
    return ApiResponseUtil.success(list);
  }

  @GetMapping("/{id}")
  @PreAuthorize("hasAnyRole('STAFF', 'ADMIN')")
  @Operation(summary = "Get coupon detail")
  public SuccessResponse<AdminCouponDto> getCoupon(@PathVariable Long id) {
    AdminCouponDto dto = adminMarketingService.getCoupon(id);
    return ApiResponseUtil.success(dto);
  }

  @PostMapping
  @PreAuthorize("hasAnyRole('STAFF', 'ADMIN')")
  @Audited(action = "CREATE_COUPON", entity = "COUPON")
  @Operation(summary = "Create new coupon with validation")
  public SuccessResponse<AdminCouponDto> createCoupon(
      @Valid @RequestBody AdminCouponRequest request,
      @CurrentUser UserPrincipal principal,
      HttpServletRequest servletRequest) {
    String ip = resolveIp(servletRequest);
    Long adminId = principal != null ? principal.id() : null;
    AdminCouponDto dto = adminMarketingService.createCoupon(request, adminId, ip);
    return ApiResponseUtil.success(dto);
  }

  @PutMapping("/{id}")
  @PreAuthorize("hasAnyRole('STAFF', 'ADMIN')")
  @Audited(action = "UPDATE_COUPON", entity = "COUPON")
  @Operation(summary = "Update coupon")
  public SuccessResponse<AdminCouponDto> updateCoupon(
      @PathVariable Long id,
      @Valid @RequestBody AdminCouponRequest request,
      @CurrentUser UserPrincipal principal,
      HttpServletRequest servletRequest) {
    String ip = resolveIp(servletRequest);
    Long adminId = principal != null ? principal.id() : null;
    AdminCouponDto dto = adminMarketingService.updateCoupon(id, request, adminId, ip);
    return ApiResponseUtil.success(dto);
  }

  @DeleteMapping("/{id}")
  @PreAuthorize("hasAnyRole('STAFF', 'ADMIN')")
  @Audited(action = "DELETE_COUPON", entity = "COUPON")
  @Operation(summary = "Delete coupon")
  public SuccessResponse<Map<String, Object>> deleteCoupon(
      @PathVariable Long id,
      @CurrentUser UserPrincipal principal,
      HttpServletRequest servletRequest) {
    String ip = resolveIp(servletRequest);
    Long adminId = principal != null ? principal.id() : null;
    adminMarketingService.deleteCoupon(id, adminId, ip);
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
