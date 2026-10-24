package com.khmermart.controller.admin;

import com.khmermart.aspect.Audited;
import com.khmermart.common.response.PaginationResponse;
import com.khmermart.common.response.SuccessResponse;
import com.khmermart.model.request.InventoryAdjustRequest;
import com.khmermart.model.response.InventoryDto;
import com.khmermart.model.response.InventoryMovementDto;
import com.khmermart.security.CurrentUser;
import com.khmermart.security.UserPrincipal;
import com.khmermart.service.AdminInventoryService;
import com.khmermart.util.ApiResponseUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/inventory")
@Tag(name = "Admin Inventory", description = "Stock monitoring, restocking and adjustments")
public class AdminInventoryController {

  private final AdminInventoryService adminInventoryService;

  public AdminInventoryController(AdminInventoryService adminInventoryService) {
    this.adminInventoryService = adminInventoryService;
  }

  @GetMapping
  @PreAuthorize("hasAnyRole('STAFF', 'ADMIN')")
  @Operation(summary = "List inventory with optional lowStock filter and pagination")
  public SuccessResponse<List<InventoryDto>> getInventory(
      @RequestParam(required = false) Boolean lowStock,
      @RequestParam(required = false) String q,
      @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.ASC) Pageable pageable) {
    Page<InventoryDto> page = adminInventoryService.getInventory(lowStock, q, pageable);
    PaginationResponse meta =
        new PaginationResponse(
            page.getNumber() + 1, page.getSize(), page.getTotalElements(), page.getTotalPages());
    return ApiResponseUtil.success(page.getContent(), meta);
  }

  @PostMapping("/{id}/restock")
  @PreAuthorize("hasAnyRole('STAFF', 'ADMIN')")
  @Audited(action = "RESTOCK_INVENTORY", entity = "INVENTORY")
  @Operation(summary = "Restock inventory units (resets low-stock alert flag)")
  public SuccessResponse<InventoryDto> restock(
      @PathVariable Long id,
      @Valid @RequestBody InventoryAdjustRequest request,
      @CurrentUser UserPrincipal principal,
      HttpServletRequest servletRequest) {
    String ip = resolveIp(servletRequest);
    Long adminId = principal != null ? principal.id() : null;
    InventoryDto dto = adminInventoryService.restock(id, request, adminId, ip);
    return ApiResponseUtil.success(dto);
  }

  @PostMapping("/{id}/adjust")
  @PreAuthorize("hasAnyRole('STAFF', 'ADMIN')")
  @Audited(action = "ADJUST_INVENTORY", entity = "INVENTORY")
  @Operation(summary = "Adjust inventory units with custom reason")
  public SuccessResponse<InventoryDto> adjust(
      @PathVariable Long id,
      @Valid @RequestBody InventoryAdjustRequest request,
      @CurrentUser UserPrincipal principal,
      HttpServletRequest servletRequest) {
    String ip = resolveIp(servletRequest);
    Long adminId = principal != null ? principal.id() : null;
    InventoryDto dto = adminInventoryService.adjust(id, request, adminId, ip);
    return ApiResponseUtil.success(dto);
  }

  @GetMapping("/{id}/movements")
  @PreAuthorize("hasAnyRole('STAFF', 'ADMIN')")
  @Operation(summary = "Get historical inventory movements for an inventory record")
  public SuccessResponse<List<InventoryMovementDto>> getMovements(@PathVariable Long id) {
    List<InventoryMovementDto> movements = adminInventoryService.getMovements(id);
    return ApiResponseUtil.success(movements);
  }

  private String resolveIp(HttpServletRequest request) {
    String xff = request.getHeader("X-Forwarded-For");
    if (xff != null && !xff.isBlank()) {
      return xff.split(",")[0].trim();
    }
    return request.getRemoteAddr();
  }
}
