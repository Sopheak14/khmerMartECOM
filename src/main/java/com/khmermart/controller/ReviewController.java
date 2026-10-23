package com.khmermart.controller;

import com.khmermart.common.response.PaginationResponse;
import com.khmermart.common.response.SuccessResponse;
import com.khmermart.model.enumerate.Roles;
import com.khmermart.model.request.CreateReviewRequest;
import com.khmermart.model.request.UpdateReviewRequest;
import com.khmermart.model.response.ReviewDto;
import com.khmermart.security.CurrentUser;
import com.khmermart.security.UserPrincipal;
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
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Reviews", description = "Product review operations")
public class ReviewController {

  private final ReviewService reviewService;

  public ReviewController(ReviewService reviewService) {
    this.reviewService = reviewService;
  }

  @PostMapping("/products/{id}/reviews")
  @PreAuthorize("isAuthenticated()")
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "Submit a product review (verified purchaser only)")
  public SuccessResponse<ReviewDto> createReview(
      @CurrentUser UserPrincipal principal,
      @PathVariable Long id,
      @Valid @RequestBody CreateReviewRequest request) {
    ReviewDto dto = reviewService.createReview(principal.id(), id, request);
    return ApiResponseUtil.success(dto);
  }

  @GetMapping("/products/{id}/reviews")
  @Operation(summary = "Get approved reviews for a product (paginated)")
  public SuccessResponse<List<ReviewDto>> getProductReviews(
      @PathVariable Long id,
      @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC)
          Pageable pageable) {
    Page<ReviewDto> page = reviewService.getProductReviews(id, pageable);
    PaginationResponse meta =
        new PaginationResponse(
            page.getNumber() + 1, page.getSize(), page.getTotalElements(), page.getTotalPages());
    return ApiResponseUtil.success(page.getContent(), meta);
  }

  @PatchMapping("/reviews/{id}")
  @PreAuthorize("isAuthenticated()")
  @Operation(summary = "Update an existing review by the reviewer")
  public SuccessResponse<ReviewDto> updateReview(
      @CurrentUser UserPrincipal principal,
      @PathVariable Long id,
      @Valid @RequestBody UpdateReviewRequest request) {
    boolean isStaffOrAdmin =
        principal != null && (principal.role() == Roles.STAFF || principal.role() == Roles.ADMIN);
    ReviewDto dto = reviewService.updateReview(id, principal.id(), request, isStaffOrAdmin);
    return ApiResponseUtil.success(dto);
  }
}
