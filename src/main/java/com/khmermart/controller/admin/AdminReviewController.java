package com.khmermart.controller.admin;

import com.khmermart.aspect.Audited;
import com.khmermart.common.response.PaginationResponse;
import com.khmermart.common.response.SuccessResponse;
import com.khmermart.model.enumerate.ReviewStatus;
import com.khmermart.model.request.AdminReviewUpdateRequest;
import com.khmermart.model.response.ReviewDto;
import com.khmermart.service.ReviewService;
import com.khmermart.util.ApiResponseUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
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
@RequestMapping("/admin/reviews")
@Tag(name = "Admin Reviews", description = "Admin review moderation and management")
public class AdminReviewController {

  private final ReviewService reviewService;

  public AdminReviewController(ReviewService reviewService) {
    this.reviewService = reviewService;
  }

  @GetMapping
  @PreAuthorize("hasAnyRole('STAFF', 'ADMIN')")
  @Operation(summary = "List customer reviews with optional status filter")
  public SuccessResponse<List<ReviewDto>> getReviews(
      @RequestParam(required = false) ReviewStatus status,
      @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
          Pageable pageable) {
    Page<ReviewDto> page = reviewService.getAdminReviews(status, pageable);
    PaginationResponse meta =
        new PaginationResponse(
            page.getNumber() + 1, page.getSize(), page.getTotalElements(), page.getTotalPages());
    return ApiResponseUtil.success(page.getContent(), meta);
  }

  @PatchMapping("/{id}")
  @PreAuthorize("hasAnyRole('STAFF', 'ADMIN')")
  @Audited(action = "MODERATE_REVIEW", entity = "REVIEW")
  @Operation(summary = "Moderate review status or add official admin reply")
  public SuccessResponse<ReviewDto> updateReviewStatus(
      @PathVariable Long id, @Valid @RequestBody AdminReviewUpdateRequest request) {
    ReviewDto dto = reviewService.adminUpdateReview(id, request);
    return ApiResponseUtil.success(dto);
  }
}
