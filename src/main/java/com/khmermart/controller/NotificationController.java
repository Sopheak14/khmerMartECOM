package com.khmermart.controller;

import com.khmermart.common.response.PaginationResponse;
import com.khmermart.common.response.SuccessResponse;
import com.khmermart.model.response.NotificationDto;
import com.khmermart.model.response.UnreadCountDto;
import com.khmermart.security.CurrentUser;
import com.khmermart.security.UserPrincipal;
import com.khmermart.service.NotificationService;
import com.khmermart.util.ApiResponseUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/notifications")
@Tag(name = "Notifications", description = "Customer notification operations")
public class NotificationController {

  private final NotificationService notificationService;

  public NotificationController(NotificationService notificationService) {
    this.notificationService = notificationService;
  }

  @GetMapping
  @PreAuthorize("isAuthenticated()")
  @Operation(summary = "Get current user notifications (paginated)")
  public SuccessResponse<List<NotificationDto>> getNotifications(
      @CurrentUser UserPrincipal principal,
      @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
          Pageable pageable) {
    Page<NotificationDto> page = notificationService.getNotifications(principal.id(), pageable);
    PaginationResponse meta =
        new PaginationResponse(
            page.getNumber() + 1, page.getSize(), page.getTotalElements(), page.getTotalPages());
    return ApiResponseUtil.success(page.getContent(), meta);
  }

  @GetMapping("/unread-count")
  @PreAuthorize("isAuthenticated()")
  @Operation(summary = "Get count of unread notifications")
  public SuccessResponse<UnreadCountDto> getUnreadCount(@CurrentUser UserPrincipal principal) {
    UnreadCountDto dto = notificationService.getUnreadCount(principal.id());
    return ApiResponseUtil.success(dto);
  }

  @PatchMapping("/{id}/read")
  @PreAuthorize("isAuthenticated()")
  @Operation(summary = "Mark single notification as read")
  public SuccessResponse<NotificationDto> markAsRead(
      @CurrentUser UserPrincipal principal, @PathVariable Long id) {
    NotificationDto dto = notificationService.markAsRead(id, principal.id());
    return ApiResponseUtil.success(dto);
  }

  @PatchMapping("/read-all")
  @PreAuthorize("isAuthenticated()")
  @Operation(summary = "Mark all unread notifications as read")
  public SuccessResponse<Map<String, Object>> markAllAsRead(@CurrentUser UserPrincipal principal) {
    notificationService.markAllAsRead(principal.id());
    return ApiResponseUtil.success(Map.of());
  }
}
