package com.khmermart.repository;

import com.khmermart.model.entity.PromotionProduct;
import com.khmermart.model.enumerate.PromotionType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface PromotionProductRepository extends JpaRepository<PromotionProduct, Long> {
  List<PromotionProduct> findByPromotionId(Long promotionId);

  List<PromotionProduct> findByProductId(Long productId);

  List<PromotionProduct> findByProductIdIn(List<Long> productIds);

  List<PromotionProduct> findByPromotionIdAndProductId(Long promotionId, Long productId);

  Optional<PromotionProduct> findByPromotionIdAndProductIdAndVariantId(
      Long promotionId, Long productId, Long variantId);

  Optional<PromotionProduct> findByPromotionIdAndProductIdAndVariantIdIsNull(
      Long promotionId, Long productId);

  /** Rows of other active promotions of a type whose time window overlaps [starts, ends). */
  @Query(
      """
            SELECT pp FROM PromotionProduct pp, Promotion p
            WHERE pp.promotionId = p.id AND p.type = :type AND p.active = true
              AND p.id <> :excludeId AND p.startsAt < :ends AND p.endsAt > :starts
            """)
  List<PromotionProduct> findInOverlappingActive(
      @Param("type") PromotionType type,
      @Param("excludeId") Long excludeId,
      @Param("starts") Instant starts,
      @Param("ends") Instant ends);

  void deleteByPromotionId(Long promotionId);
}
