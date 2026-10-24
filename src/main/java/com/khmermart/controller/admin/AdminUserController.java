package com.khmermart.controller.admin;

import com.khmermart.aspect.Audited;
import com.khmermart.common.response.PaginationResponse;
import com.khmermart.common.response.SuccessResponse;
import com.khmermart.model.enumerate.Roles;
import com.khmermart.model.request.AdminUserUpdateRequest;
import com.khmermart.model.response.AppUserDto;
import com.khmermart.security.CurrentUser;
import com.khmermart.security.UserPrincipal;
import com.khmermart.service.AdminUserService;
import com.khmermart.util.ApiResponseUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/users")
@Tag(name = "Admin Users", description = "User account administration and role management")
public class AdminUserController {

  private final AdminUserService adminUserService;

  public AdminUserController(AdminUserService adminUserService) {
    this.adminUserService = adminUserService;
  }

  @GetMapping
  @PreAuthorize("hasRole('ADMIN')")
  @Operation(summary = "Search and list users with pagination")
  public SuccessResponse<List<AppUserDto>> getUsers(
      @RequestParam(required = false) String q,
      @RequestParam(required = false) Roles role,
      @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
          Pageable pageable) {
    Page<AppUserDto> page = adminUserService.getUsers(q, role, pageable);
    PaginationResponse meta =
        new PaginationResponse(
            page.getNumber() + 1, page.getSize(), page.getTotalElements(), page.getTotalPages());
    return ApiResponseUtil.success(page.getContent(), meta);
  }

  @PatchMapping("/{id}")
  @PreAuthorize("hasRole('ADMIN')")
  @Audited(action = "UPDATE_USER", entity = "USER")
  @Operation(summary = "Update user role or active status")
  public SuccessResponse<AppUserDto> updateUser(
      @PathVariable Long id,
      @RequestBody AdminUserUpdateRequest request,
      @CurrentUser UserPrincipal principal,
      HttpServletRequest servletRequest) {
    String clientIp = resolveIp(servletRequest);
    Long adminId = principal != null ? principal.id() : null;
    AppUserDto dto = adminUserService.updateUser(id, request, adminId, clientIp);
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
