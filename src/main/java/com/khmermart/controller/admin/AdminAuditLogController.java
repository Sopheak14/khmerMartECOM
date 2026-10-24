package com.khmermart.controller.admin;

import com.khmermart.common.response.PaginationResponse;
import com.khmermart.common.response.SuccessResponse;
import com.khmermart.model.response.AuditLogDto;
import com.khmermart.service.AuditLogService;
import com.khmermart.util.ApiResponseUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/audit-logs")
@Tag(name = "Admin Audit Logs", description = "System security and administrative audit trail")
public class AdminAuditLogController {

  private final AuditLogService auditLogService;

  public AdminAuditLogController(AuditLogService auditLogService) {
    this.auditLogService = auditLogService;
  }

  @GetMapping
  @PreAuthorize("hasRole('ADMIN')")
  @Operation(summary = "List audit logs with optional actor and entity filters")
  public SuccessResponse<List<AuditLogDto>> getAuditLogs(
      @RequestParam(required = false) Long actor,
      @RequestParam(required = false) String entity,
      @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
          Pageable pageable) {
    Page<AuditLogDto> page = auditLogService.getAuditLogs(actor, entity, pageable);
    PaginationResponse meta =
        new PaginationResponse(
            page.getNumber() + 1, page.getSize(), page.getTotalElements(), page.getTotalPages());
    return ApiResponseUtil.success(page.getContent(), meta);
  }
}
