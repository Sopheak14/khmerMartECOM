package com.khmermart.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.khmermart.model.entity.Promotion;
import com.khmermart.model.entity.PromotionProduct;
import com.khmermart.model.enumerate.PromotionType;
import com.khmermart.repository.ProductVariantRepository;
import com.khmermart.repository.PromotionProductRepository;
import com.khmermart.seed.SeedCatalog;
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

  private static final long HERO = SeedCatalog.HERO_ID;
  private static final long RED_STD = SeedCatalog.HERO_RED_STANDARD;
  private static final long RED_PREMIUM = SeedCatalog.HERO_RED_PREMIUM;
  private static final long BLUE_PREMIUM = SeedCatalog.HERO_BLUE_PREMIUM;
  private static final long FLASH = SeedCatalog.HERO_FLASH_CENTS;

  @Test
  @DisplayName("Variant in two active flash sales: the lowest sale price (best discount) wins")
  void lowestPriceAcrossActiveFlashSalesWins() {
    when(rows.findByPromotionIdAndProductIdAndVariantId(1L, HERO, RED_STD))
        .thenReturn(Optional.of(row(1, HERO, RED_STD, FLASH)));
    when(rows.findByPromotionIdAndProductIdAndVariantId(2L, HERO, RED_STD))
        .thenReturn(Optional.of(row(2, HERO, RED_STD, FLASH - 1000L)));

    Long price =
        resolver.resolve(
            List.of(
                promo(1, PromotionType.FLASH_SALE),
                promo(2, PromotionType.FLASH_SALE),
                promo(3, PromotionType.SEASONAL)),
            HERO,
            RED_STD);

    assertThat(price).isEqualTo(FLASH - 1000L);
  }

  @Test
  @DisplayName("Different variants in different flash sales each resolve to their own price")
  void differentVariantsResolveIndependently() {
    long premiumSale = SeedCatalog.HERO_PREMIUM_CENTS - 2000L;
    when(rows.findByPromotionIdAndProductIdAndVariantId(1L, HERO, RED_STD))
        .thenReturn(Optional.of(row(1, HERO, RED_STD, FLASH)));
    when(rows.findByPromotionIdAndProductIdAndVariantId(2L, HERO, RED_PREMIUM))
        .thenReturn(Optional.of(row(2, HERO, RED_PREMIUM, premiumSale)));
    List<Promotion> active =
        List.of(promo(1, PromotionType.FLASH_SALE), promo(2, PromotionType.FLASH_SALE));

    assertThat(resolver.resolve(active, HERO, RED_STD)).isEqualTo(FLASH);
    assertThat(resolver.resolve(active, HERO, RED_PREMIUM)).isEqualTo(premiumSale);
    assertThat(resolver.resolve(active, HERO, BLUE_PREMIUM)).isNull();
  }

  @Test
  @DisplayName("A product-wide row never applies to a product that has variants")
  void productWideSaleIgnoredWhenProductHasVariants() {
    when(variants.existsByProductId(HERO)).thenReturn(true);
    when(rows.findByPromotionIdAndProductIdAndVariantIdIsNull(1L, HERO))
        .thenReturn(Optional.of(row(1, HERO, null, FLASH)));

    assertThat(resolver.resolve(List.of(promo(1, PromotionType.FLASH_SALE)), HERO, null)).isNull();
  }
}
