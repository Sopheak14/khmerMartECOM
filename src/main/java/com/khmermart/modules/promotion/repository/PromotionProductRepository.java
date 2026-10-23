package com.khmermart.modules.promotion.repository;

import com.khmermart.modules.promotion.entity.PromotionProduct;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PromotionProductRepository
    extends JpaRepository<PromotionProduct, PromotionProduct.PromotionProductId> {
  List<PromotionProduct> findByPromotionId(Long promotionId);

  List<PromotionProduct> findByProductId(Long productId);

  List<PromotionProduct> findByProductIdIn(List<Long> productIds);
}
