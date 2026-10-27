package com.khmermart.service.impl;

import com.khmermart.model.entity.Promotion;
import com.khmermart.model.entity.PromotionProduct;
import com.khmermart.model.enumerate.PromotionType;
import com.khmermart.repository.ProductVariantRepository;
import com.khmermart.repository.PromotionProductRepository;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Component;

/**
 * Resolves the flash sale price of one cart line across ALL active flash sales: the lowest price
 * wins (the customer's best discount), so a line still gets at most one automatic promotion.
 */
@Component
public class FlashSalePriceResolver {

  private final PromotionProductRepository promotionProductRepository;
  private final ProductVariantRepository variantRepository;

  public FlashSalePriceResolver(
      PromotionProductRepository promotionProductRepository,
      ProductVariantRepository variantRepository) {
    this.promotionProductRepository = promotionProductRepository;
    this.variantRepository = variantRepository;
  }

  /** variantId null means a product without variants; a variant sale never applies product-wide. */
  public Long resolve(List<Promotion> activePromotions, Long productId, Long variantId) {
    List<Promotion> flashSales =
        activePromotions.stream().filter(p -> p.getType() == PromotionType.FLASH_SALE).toList();
    if (flashSales.isEmpty()) {
      return null;
    }
    if (variantId == null && variantRepository.existsByProductId(productId)) {
      return null;
    }
    return flashSales.stream()
        .map(
            promo ->
                variantId != null
                    ? promotionProductRepository.findByPromotionIdAndProductIdAndVariantId(
                        promo.getId(), productId, variantId)
                    : promotionProductRepository.findByPromotionIdAndProductIdAndVariantIdIsNull(
                        promo.getId(), productId))
        .flatMap(java.util.Optional::stream)
        .map(PromotionProduct::getSalePriceCents)
        .filter(Objects::nonNull)
        .min(Long::compare)
        .orElse(null);
  }
}
