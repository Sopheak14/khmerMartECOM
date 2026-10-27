package com.khmermart.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.khmermart.model.entity.Promotion;
import com.khmermart.model.entity.PromotionProduct;
import com.khmermart.model.enumerate.PromotionType;
import com.khmermart.repository.ProductVariantRepository;
import com.khmermart.repository.PromotionProductRepository;
import com.khmermart.service.impl.FlashSalePriceResolver;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class FlashSalePriceResolverTest {

  private final PromotionProductRepository rows = mock(PromotionProductRepository.class);
  private final ProductVariantRepository variants = mock(ProductVariantRepository.class);
  private final FlashSalePriceResolver resolver = new FlashSalePriceResolver(rows, variants);

  private Promotion promo(long id, PromotionType type) {
    return Promotion.builder().id(id).type(type).active(true).build();
  }

  private PromotionProduct row(long promoId, long productId, Long variantId, long price) {
    return PromotionProduct.builder()
        .promotionId(promoId)
        .productId(productId)
        .variantId(variantId)
        .salePriceCents(price)
        .build();
  }

  @Test
  @DisplayName("Variant in two active flash sales: the lowest sale price (best discount) wins")
  void lowestPriceAcrossActiveFlashSalesWins() {
    when(rows.findByPromotionIdAndProductIdAndVariantId(1L, 1001L, 3L))
        .thenReturn(Optional.of(row(1, 1001, 3L, 120000)));
    when(rows.findByPromotionIdAndProductIdAndVariantId(2L, 1001L, 3L))
        .thenReturn(Optional.of(row(2, 1001, 3L, 119900)));

    Long price =
        resolver.resolve(
            List.of(
                promo(1, PromotionType.FLASH_SALE),
                promo(2, PromotionType.FLASH_SALE),
                promo(3, PromotionType.SEASONAL)),
            1001L,
            3L);

    assertThat(price).isEqualTo(119900L);
  }

  @Test
  @DisplayName("Different variants in different flash sales each resolve to their own price")
  void differentVariantsResolveIndependently() {
    when(rows.findByPromotionIdAndProductIdAndVariantId(1L, 1001L, 1L))
        .thenReturn(Optional.of(row(1, 1001, 1L, 89900)));
    when(rows.findByPromotionIdAndProductIdAndVariantId(2L, 1001L, 3L))
        .thenReturn(Optional.of(row(2, 1001, 3L, 119900)));
    List<Promotion> active =
        List.of(promo(1, PromotionType.FLASH_SALE), promo(2, PromotionType.FLASH_SALE));

    assertThat(resolver.resolve(active, 1001L, 1L)).isEqualTo(89900L);
    assertThat(resolver.resolve(active, 1001L, 3L)).isEqualTo(119900L);
    assertThat(resolver.resolve(active, 1001L, 4L)).isNull();
  }

  @Test
  @DisplayName("A product-wide row never applies to a product that has variants")
  void productWideSaleIgnoredWhenProductHasVariants() {
    when(variants.existsByProductId(1001L)).thenReturn(true);
    when(rows.findByPromotionIdAndProductIdAndVariantIdIsNull(1L, 1001L))
        .thenReturn(Optional.of(row(1, 1001, null, 50000)));

    assertThat(resolver.resolve(List.of(promo(1, PromotionType.FLASH_SALE)), 1001L, null))
        .isNull();
  }
}
