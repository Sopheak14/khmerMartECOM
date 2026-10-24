package com.khmermart.controller.admin;

import com.khmermart.aspect.Audited;
import com.khmermart.common.response.SuccessResponse;
import com.khmermart.model.request.CategoryRequest;
import com.khmermart.model.response.CategoryDto;
import com.khmermart.security.CurrentUser;
import com.khmermart.security.UserPrincipal;
import com.khmermart.service.AdminCategoryService;
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
@RequestMapping("/admin/categories")
@Tag(name = "Admin Categories", description = "Category management operations")
public class AdminCategoryController {

  private final AdminCategoryService adminCategoryService;

  public AdminCategoryController(AdminCategoryService adminCategoryService) {
    this.adminCategoryService = adminCategoryService;
  }

  @GetMapping
  @PreAuthorize("hasAnyRole('STAFF', 'ADMIN')")
  @Operation(summary = "List all categories")
  public SuccessResponse<List<CategoryDto>> getCategories() {
    List<CategoryDto> list = adminCategoryService.getAllCategories();
    return ApiResponseUtil.success(list);
  }

  @PostMapping
  @PreAuthorize("hasAnyRole('STAFF', 'ADMIN')")
  @Audited(action = "CREATE_CATEGORY", entity = "CATEGORY")
  @Operation(summary = "Create new category")
  public SuccessResponse<CategoryDto> createCategory(
      @Valid @RequestBody CategoryRequest request,
      @CurrentUser UserPrincipal principal,
      HttpServletRequest servletRequest) {
    String ip = resolveIp(servletRequest);
    Long adminId = principal != null ? principal.id() : null;
    CategoryDto dto = adminCategoryService.createCategory(request, adminId, ip);
    return ApiResponseUtil.success(dto);
  }

  @PutMapping("/{id}")
  @PreAuthorize("hasAnyRole('STAFF', 'ADMIN')")
  @Audited(action = "UPDATE_CATEGORY", entity = "CATEGORY")
  @Operation(summary = "Update category")
  public SuccessResponse<CategoryDto> updateCategory(
      @PathVariable Long id,
      @Valid @RequestBody CategoryRequest request,
      @CurrentUser UserPrincipal principal,
      HttpServletRequest servletRequest) {
    String ip = resolveIp(servletRequest);
    Long adminId = principal != null ? principal.id() : null;
    CategoryDto dto = adminCategoryService.updateCategory(id, request, adminId, ip);
    return ApiResponseUtil.success(dto);
  }

  @DeleteMapping("/{id}")
  @PreAuthorize("hasAnyRole('STAFF', 'ADMIN')")
  @Audited(action = "DELETE_CATEGORY", entity = "CATEGORY")
  @Operation(summary = "Delete category (fails with 409 if category has products)")
  public SuccessResponse<Map<String, Object>> deleteCategory(
      @PathVariable Long id,
      @CurrentUser UserPrincipal principal,
      HttpServletRequest servletRequest) {
    String ip = resolveIp(servletRequest);
    Long adminId = principal != null ? principal.id() : null;
    adminCategoryService.deleteCategory(id, adminId, ip);
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
