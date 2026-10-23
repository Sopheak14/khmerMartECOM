package com.khmermart.service;

import com.khmermart.model.request.AdminReviewUpdateRequest;
import com.khmermart.model.request.CreateReviewRequest;
import com.khmermart.model.request.UpdateReviewRequest;
import com.khmermart.model.response.ReviewDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ReviewService {
  void recomputeProductRating(Long productId);

  ReviewDto createReview(Long userId, Long productId, CreateReviewRequest request);

  Page<ReviewDto> getProductReviews(Long productId, Pageable pageable);

  ReviewDto updateReview(
      Long reviewId, Long userId, UpdateReviewRequest request, boolean isStaffOrAdmin);

  ReviewDto adminUpdateReview(Long reviewId, AdminReviewUpdateRequest request);
}
