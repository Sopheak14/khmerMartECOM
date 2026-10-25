package com.khmermart.repository;

import com.khmermart.model.entity.PromotionProduct;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
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

  void deleteByPromotionId(Long promotionId);
}
