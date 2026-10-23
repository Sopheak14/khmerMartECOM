package com.khmermart.controller.admin;

import com.khmermart.common.response.SuccessResponse;
import com.khmermart.model.request.AdminReviewUpdateRequest;
import com.khmermart.model.response.ReviewDto;
import com.khmermart.service.ReviewService;
import com.khmermart.util.ApiResponseUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/reviews")
@Tag(name = "Admin Reviews", description = "Admin review moderation and management")
public class AdminReviewController {

  private final ReviewService reviewService;

  public AdminReviewController(ReviewService reviewService) {
    this.reviewService = reviewService;
  }

  @PatchMapping("/{id}")
  @PreAuthorize("hasAnyRole('STAFF', 'ADMIN')")
  @Operation(summary = "Moderate review status or add official admin reply")
  public SuccessResponse<ReviewDto> updateReviewStatus(
      @PathVariable Long id, @Valid @RequestBody AdminReviewUpdateRequest request) {
    ReviewDto dto = reviewService.adminUpdateReview(id, request);
    return ApiResponseUtil.success(dto);
  }
}
