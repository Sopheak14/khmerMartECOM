package com.khmermart.repository;

import com.khmermart.model.entity.Review;
import com.khmermart.model.enumerate.ReviewStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ReviewRepository extends JpaRepository<Review, Long> {
  Page<Review> findByProductIdAndStatus(Long productId, ReviewStatus status, Pageable pageable);

  Optional<Review> findByUserIdAndProductId(Long userId, Long productId);

  boolean existsByUserIdAndProductId(Long userId, Long productId);

  Page<Review> findByStatus(ReviewStatus status, Pageable pageable);

  @Query(
      "SELECT r.rating, COUNT(r) FROM Review r WHERE r.productId = :productId AND r.status = 'APPROVED' GROUP BY r.rating")
  List<Object[]> countRatingsByProduct(@Param("productId") Long productId);
}
