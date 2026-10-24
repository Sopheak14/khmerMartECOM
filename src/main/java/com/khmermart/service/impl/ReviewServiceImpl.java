package com.khmermart.service.impl;

import com.khmermart.common.exception.CustomException;
import com.khmermart.common.exception.ErrorCode;
import com.khmermart.event.ReviewRepliedEvent;
import com.khmermart.model.entity.AppUser;
import com.khmermart.model.entity.Order;
import com.khmermart.model.entity.OrderItem;
import com.khmermart.model.entity.Product;
import com.khmermart.model.entity.Review;
import com.khmermart.model.enumerate.OrderStatus;
import com.khmermart.model.enumerate.ReviewStatus;
import com.khmermart.model.request.AdminReviewUpdateRequest;
import com.khmermart.model.request.CreateReviewRequest;
import com.khmermart.model.request.UpdateReviewRequest;
import com.khmermart.model.response.ReviewDto;
import com.khmermart.model.response.ReviewUserDto;
import com.khmermart.repository.AppUserRepository;
import com.khmermart.repository.OrderItemRepository;
import com.khmermart.repository.OrderRepository;
import com.khmermart.repository.ProductRepository;
import com.khmermart.repository.ReviewRepository;
import com.khmermart.service.ReviewService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReviewServiceImpl implements ReviewService {

  private final ReviewRepository reviewRepository;
  private final ProductRepository productRepository;
  private final OrderRepository orderRepository;
  private final OrderItemRepository orderItemRepository;
  private final AppUserRepository appUserRepository;
  private final ApplicationEventPublisher eventPublisher;

  public ReviewServiceImpl(
      ReviewRepository reviewRepository,
      ProductRepository productRepository,
      OrderRepository orderRepository,
      OrderItemRepository orderItemRepository,
      AppUserRepository appUserRepository,
      ApplicationEventPublisher eventPublisher) {
    this.reviewRepository = reviewRepository;
    this.productRepository = productRepository;
    this.orderRepository = orderRepository;
    this.orderItemRepository = orderItemRepository;
    this.appUserRepository = appUserRepository;
    this.eventPublisher = eventPublisher;
  }

  @Override
  @Transactional
  public void recomputeProductRating(Long productId) {
    Product product = productRepository.findById(productId).orElse(null);
    if (product == null) {
      return;
    }

    List<Object[]> ratings = reviewRepository.countRatingsByProduct(productId);
    long totalCount = 0;
    long totalScore = 0;

    for (Object[] row : ratings) {
      int star = ((Number) row[0]).intValue();
      long count = ((Number) row[1]).longValue();
      totalCount += count;
      totalScore += ((long) star * count);
    }

    if (totalCount > 0) {
      double avg = (double) totalScore / totalCount;
      BigDecimal rounded = BigDecimal.valueOf(avg).setScale(1, RoundingMode.HALF_UP);
      product.setAvgRating(rounded);
      product.setReviewCount((int) totalCount);
    } else {
      product.setAvgRating(BigDecimal.ZERO);
      product.setReviewCount(0);
    }

    productRepository.save(product);
  }

  @Override
  @Transactional
  public ReviewDto createReview(Long userId, Long productId, CreateReviewRequest request) {
    productRepository
        .findById(productId)
        .orElseThrow(() -> new CustomException(ErrorCode.NOT_FOUND, "Product not found"));

    // 1. Verified purchaser check: user must have a DELIVERED order containing this product
    List<Order> deliveredOrders =
        orderRepository
            .findByUserIdAndStatus(userId, OrderStatus.DELIVERED, Pageable.unpaged())
            .getContent();

    Long orderItemId = null;
    boolean hasPurchased = false;
    for (Order order : deliveredOrders) {
      List<OrderItem> items = orderItemRepository.findByOrderId(order.getId());
      for (OrderItem item : items) {
        if (item.getProductId().equals(productId)) {
          hasPurchased = true;
          orderItemId = item.getId();
          break;
        }
      }
      if (hasPurchased) {
        break;
      }
    }

    if (!hasPurchased) {
      throw new CustomException(ErrorCode.FORBIDDEN, "Only verified purchasers can review");
    }

    // 2. Prevent duplicate review
    if (reviewRepository.existsByUserIdAndProductId(userId, productId)) {
      throw new CustomException(ErrorCode.CONFLICT, "Use PATCH /api/reviews/{id} to edit");
    }

    Instant now = Instant.now();
    Review review =
        Review.builder()
            .userId(userId)
            .productId(productId)
            .orderItemId(orderItemId)
            .rating(request.rating())
            .title(request.title())
            .body(request.body())
            .status(ReviewStatus.PENDING)
            .createdAt(now)
            .updatedAt(now)
            .build();

    review = reviewRepository.save(review);
    return toDto(review);
  }

  @Override
  @Transactional(readOnly = true)
  public Page<ReviewDto> getProductReviews(Long productId, Pageable pageable) {
    return reviewRepository
        .findByProductIdAndStatus(productId, ReviewStatus.APPROVED, pageable)
        .map(this::toDto);
  }

  @Override
  @Transactional
  public ReviewDto updateReview(
      Long reviewId, Long userId, UpdateReviewRequest request, boolean isStaffOrAdmin) {
    Review review =
        reviewRepository
            .findById(reviewId)
            .orElseThrow(() -> new CustomException(ErrorCode.NOT_FOUND, "Review not found"));

    if (userId != null && !review.getUserId().equals(userId) && !isStaffOrAdmin) {
      throw new CustomException(ErrorCode.FORBIDDEN, "Access denied to review");
    }

    if (request.rating() != null) {
      review.setRating(request.rating());
    }
    if (request.title() != null) {
      review.setTitle(request.title());
    }
    if (request.body() != null) {
      review.setBody(request.body());
    }
    review.setUpdatedAt(Instant.now());
    review = reviewRepository.save(review);

    if (review.getStatus() == ReviewStatus.APPROVED) {
      recomputeProductRating(review.getProductId());
    }

    return toDto(review);
  }

  @Override
  @Transactional
  public ReviewDto adminUpdateReview(Long reviewId, AdminReviewUpdateRequest request) {
    Review review =
        reviewRepository
            .findById(reviewId)
            .orElseThrow(() -> new CustomException(ErrorCode.NOT_FOUND, "Review not found"));

    ReviewStatus oldStatus = review.getStatus();
    review.setStatus(request.status());

    if (request.adminReply() != null) {
      review.setAdminReply(request.adminReply());
    }
    review.setUpdatedAt(Instant.now());
    review = reviewRepository.save(review);

    if (request.status() == ReviewStatus.APPROVED || oldStatus == ReviewStatus.APPROVED) {
      recomputeProductRating(review.getProductId());
    }

    if (request.adminReply() != null && !request.adminReply().isBlank()) {
      AppUser user = appUserRepository.findById(review.getUserId()).orElse(null);
      Product product = productRepository.findById(review.getProductId()).orElse(null);
      String productName =
          product != null ? product.getName() : "Product #" + review.getProductId();

      eventPublisher.publishEvent(
          new ReviewRepliedEvent(
              review.getId(),
              review.getProductId(),
              productName,
              review.getUserId(),
              user != null ? user.getEmail() : null,
              user != null ? user.getFullName() : null,
              request.adminReply()));
    }

    return toDto(review);
  }

  @Override
  @Transactional(readOnly = true)
  public Page<ReviewDto> getAdminReviews(ReviewStatus status, Pageable pageable) {
    Page<Review> page;
    if (status != null) {
      page = reviewRepository.findByStatus(status, pageable);
    } else {
      page = reviewRepository.findAll(pageable);
    }
    return page.map(this::toDto);
  }

  private ReviewDto toDto(Review r) {
    AppUser user = appUserRepository.findById(r.getUserId()).orElse(null);
    String fullName = user != null ? user.getFullName() : "Customer";
    return new ReviewDto(
        r.getId(),
        r.getProductId(),
        new ReviewUserDto(fullName),
        r.getRating(),
        r.getTitle(),
        r.getBody(),
        r.getOrderItemId() != null,
        r.getStatus(),
        r.getAdminReply(),
        r.getCreatedAt());
  }
}
