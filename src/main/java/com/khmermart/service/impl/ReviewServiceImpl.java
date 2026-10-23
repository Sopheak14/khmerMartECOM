package com.khmermart.service.impl;

import com.khmermart.model.entity.Product;
import com.khmermart.repository.ProductRepository;
import com.khmermart.repository.ReviewRepository;
import com.khmermart.service.ReviewService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReviewServiceImpl implements ReviewService {

  private final ReviewRepository reviewRepository;
  private final ProductRepository productRepository;

  public ReviewServiceImpl(ReviewRepository reviewRepository, ProductRepository productRepository) {
    this.reviewRepository = reviewRepository;
    this.productRepository = productRepository;
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
}
