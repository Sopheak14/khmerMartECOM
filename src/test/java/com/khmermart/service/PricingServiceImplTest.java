package com.khmermart.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.khmermart.model.entity.Coupon;
import com.khmermart.model.entity.Promotion;
import com.khmermart.model.entity.ShippingMethod;
import com.khmermart.model.enumerate.CouponType;
import com.khmermart.model.enumerate.PromotionType;
import com.khmermart.seed.SeedCatalog;
import com.khmermart.service.impl.PricingServiceImpl;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PricingServiceImplTest {

  private PricingServiceImpl pricingService;

  @BeforeEach
  void setUp() {
    pricingService = new PricingServiceImpl();
  }

  @Test
  @DisplayName(
      "1. Fixed coupon: A 1000c x2 + B 1500c x1 with SAVE5 ($5) -> subtotal 3500, discount 500, total 3000")
  void testFixedCouponSave5() {
    List<PricingItem> items =
        List.of(
            new PricingItem(1L, null, 1000L, 2, null), new PricingItem(2L, null, 1500L, 1, null));

    Coupon save5 =
        Coupon.builder()
            .code("SAVE5")
            .type(CouponType.FIXED)
            .value(500L)
            .minOrderCents(3500L)
            .active(true)
            .build();

    PricingResult result = pricingService.calculate(items, List.of(), save5, null);

    assertThat(result.subtotalCents()).isEqualTo(3500L);
    assertThat(result.discountCents()).isEqualTo(500L);
    assertThat(result.totalCents()).isEqualTo(3000L);
    assertThat(result.coupon()).isNotNull();
    assertThat(result.coupon().code()).isEqualTo("SAVE5");
  }

  @Test
  @DisplayName(
      "2. Percent coupon: 10000c with 20% discount -> subtotal 10000, discount 2000, total 8000")
  void testPercentCoupon() {
    List<PricingItem> items = List.of(new PricingItem(1L, null, 10000L, 1, null));

    Coupon discount20 =
        Coupon.builder().code("OFF20").type(CouponType.PERCENT).value(20L).active(true).build();

    PricingResult result = pricingService.calculate(items, List.of(), discount20, null);

    assertThat(result.subtotalCents()).isEqualTo(10000L);
    assertThat(result.discountCents()).isEqualTo(2000L);
    assertThat(result.totalCents()).isEqualTo(8000L);
  }

  @Test
  @DisplayName(
      "3. Percent coupon with cap: WELCOME10 (10% capped at $20 / 2000c) on $250 / 25000c order -> discount 2000c")
  void testPercentCouponWithCap() {
    List<PricingItem> items = List.of(new PricingItem(1L, null, 25000L, 1, null));

    Coupon welcome10 =
        Coupon.builder()
            .code("WELCOME10")
            .type(CouponType.PERCENT)
            .value(10L)
            .maxDiscountCents(2000L)
            .active(true)
            .build();

    PricingResult result = pricingService.calculate(items, List.of(), welcome10, null);

    assertThat(result.subtotalCents()).isEqualTo(25000L);
    assertThat(result.discountCents()).isEqualTo(2000L);
    assertThat(result.totalCents()).isEqualTo(23000L);
  }

  @Test
  @DisplayName(
      "4. Shipping rules: standard fee 150 waived when subtotal-after-discounts >= freeOverCents (5000) or with FREESHIP coupon")
  void testFreeShipping() {
    ShippingMethod standard =
        ShippingMethod.builder().code("STANDARD").feeCents(150L).freeOverCents(5000L).build();

    // Below threshold ($28 / 2800c), no coupon -> shipping fee 150, total 2950
    List<PricingItem> smallOrder = List.of(new PricingItem(1L, null, 2800L, 1, null));
    PricingResult res1 = pricingService.calculate(smallOrder, List.of(), null, standard);
    assertThat(res1.shippingCents()).isEqualTo(150L);
    assertThat(res1.totalCents()).isEqualTo(2950L);

    // Below threshold with FREESHIP coupon -> shipping fee 0, total 2800
    Coupon freeShipCoupon =
        Coupon.builder()
            .code("FREESHIP")
            .type(CouponType.FREE_SHIPPING)
            .value(0L)
            .active(true)
            .build();
    PricingResult res2 = pricingService.calculate(smallOrder, List.of(), freeShipCoupon, standard);
    assertThat(res2.shippingCents()).isEqualTo(0L);
    assertThat(res2.totalCents()).isEqualTo(2800L);

    // Above threshold ($60 / 6000c) -> shipping fee 0
    List<PricingItem> largeOrder = List.of(new PricingItem(1L, null, 6000L, 1, null));
    PricingResult res3 = pricingService.calculate(largeOrder, List.of(), null, standard);
    assertThat(res3.shippingCents()).isEqualTo(0L);
    assertThat(res3.totalCents()).isEqualTo(6000L);

    // Subtotal 6000c minus discount 2000c = 4000c (< 5000c threshold) -> shipping fee 150 applies!
    Coupon coupon2000 =
        Coupon.builder().code("OFF2000").type(CouponType.FIXED).value(2000L).active(true).build();
    PricingResult res4 = pricingService.calculate(largeOrder, List.of(), coupon2000, standard);
    assertThat(res4.discountCents()).isEqualTo(2000L);
    assertThat(res4.shippingCents()).isEqualTo(150L);
    assertThat(res4.totalCents()).isEqualTo(4150L); // 6000 - 2000 + 150
  }

  @Test
  @DisplayName("5. Stacking: Flash sale automatic promotion first, then coupon stacks on remainder")
  void testPromotionAndCouponStacking() {
    // Regular price 5000c, flash sale price 4000c (1000c discount)
    List<PricingItem> items = List.of(new PricingItem(1L, null, 5000L, 1, 4000L));

    Promotion flashSale =
        Promotion.builder()
            .name("Flash Sale")
            .type(PromotionType.FLASH_SALE)
            .active(true)
            .startsAt(Instant.now().minusSeconds(3600))
            .endsAt(Instant.now().plusSeconds(3600))
            .build();

    // 10% coupon on remaining 4000c -> 400c discount
    Coupon coupon =
        Coupon.builder().code("EXTRA10").type(CouponType.PERCENT).value(10L).active(true).build();

    PricingResult result = pricingService.calculate(items, List.of(flashSale), coupon, null);

    assertThat(result.subtotalCents()).isEqualTo(5000L);
    assertThat(result.promotionDiscounts()).hasSize(1);
    assertThat(result.promotionDiscounts().get(0).amountCents()).isEqualTo(1000L);
    // Total discount = 1000 (flash) + 400 (coupon) = 1400
    assertThat(result.discountCents()).isEqualTo(1400L);
    assertThat(result.totalCents()).isEqualTo(3600L);
  }

  @Test
  @DisplayName("6. Minimums: total cents is floored at 0 even if discounts exceed subtotal")
  void testDiscountExceedingSubtotalFlooredAtZero() {
    List<PricingItem> items = List.of(new PricingItem(1L, null, 300L, 1, null));

    Coupon hugeDiscount =
        Coupon.builder().code("HUGE500").type(CouponType.FIXED).value(500L).active(true).build();

    PricingResult result = pricingService.calculate(items, List.of(), hugeDiscount, null);

    assertThat(result.subtotalCents()).isEqualTo(300L);
    assertThat(result.discountCents()).isEqualTo(300L);
    assertThat(result.totalCents()).isEqualTo(0L);
  }

  @Test
  @DisplayName("7. Hol Silk Sampot in flash sale + seasonal -> only the flash sale applies")
  void testFlashSaleWithActiveSeasonalPromo() {
    // Hero Standard: price 14900c, flash sale price 12900c (not linked to seasonal)
    List<PricingItem> items =
        List.of(
            new PricingItem(
                SeedCatalog.HERO_ID,
                SeedCatalog.HERO_RED_STANDARD,
                SeedCatalog.HERO_STANDARD_CENTS,
                1,
                SeedCatalog.HERO_FLASH_CENTS,
                false,
                null));

    Promotion flashSale =
        Promotion.builder().name("Flash Sale").type(PromotionType.FLASH_SALE).active(true).build();

    Promotion seasonal =
        Promotion.builder()
            .name("Seasonal 10%")
            .type(PromotionType.SEASONAL)
            .discountPercent(SeedCatalog.SEASONAL_PERCENT)
            .thresholdCents(5000L)
            .active(true)
            .build();

    PricingResult result =
        pricingService.calculate(items, List.of(flashSale, seasonal), null, null);

    assertThat(result.subtotalCents()).isEqualTo(SeedCatalog.HERO_STANDARD_CENTS);
    assertThat(result.discountCents()).isEqualTo(SeedCatalog.DEMO_ORDER_FLASH_DISCOUNT_CENTS);
    assertThat(result.totalCents()).isEqualTo(SeedCatalog.HERO_FLASH_CENTS);
    assertThat(result.promotionDiscounts()).hasSize(1);
    assertThat(result.promotionDiscounts().get(0).label()).isEqualTo("Flash Sale Discount");
  }

  @Test
  @DisplayName("8. Seasonal-only product -> Pchum Ben 10% off a Spices & Sauces price")
  void testSeasonalOnlyProduct() {
    // Product 1007 (Kampot Red Pepper 100 g, $12.50 = 1250c) with seasonal 10%
    long price = SeedCatalog.product(1007L).priceCents();
    long seasonalDiscount = price * SeedCatalog.SEASONAL_PERCENT / 100;
    List<PricingItem> items =
        List.of(new PricingItem(1007L, null, price, 1, null, false, SeedCatalog.SEASONAL_PERCENT));

    Promotion seasonal =
        Promotion.builder()
            .name(SeedCatalog.SEASONAL_NAME)
            .type(PromotionType.SEASONAL)
            .discountPercent(SeedCatalog.SEASONAL_PERCENT)
            .thresholdCents(0L)
            .active(true)
            .build();

    PricingResult result = pricingService.calculate(items, List.of(seasonal), null, null);

    assertThat(result.subtotalCents()).isEqualTo(price);
    assertThat(result.discountCents()).isEqualTo(seasonalDiscount);
    assertThat(result.totalCents()).isEqualTo(price - seasonalDiscount);
    assertThat(result.promotionDiscounts()).hasSize(1);
    assertThat(result.promotionDiscounts().get(0).label()).contains("Seasonal");
  }

  @Test
  @DisplayName("9. Flash item + SAVE5 -> 14900 - 2000 - 500 = 12400 (demo order)")
  void testFlashSaleItemWithSave5Coupon() {
    // Hero Standard (14900c, flash price 12900c) + coupon SAVE5 (500c)
    List<PricingItem> items =
        List.of(
            new PricingItem(
                SeedCatalog.HERO_ID,
                SeedCatalog.HERO_RED_STANDARD,
                SeedCatalog.HERO_STANDARD_CENTS,
                1,
                SeedCatalog.HERO_FLASH_CENTS,
                false,
                null));

    Promotion flashSale =
        Promotion.builder().name("Flash Sale").type(PromotionType.FLASH_SALE).active(true).build();

    Coupon save5 =
        Coupon.builder()
            .code(SeedCatalog.COUPON_SAVE5)
            .type(CouponType.FIXED)
            .value(SeedCatalog.SAVE5_CENTS)
            .minOrderCents(SeedCatalog.SAVE5_MIN_ORDER_CENTS)
            .active(true)
            .build();

    PricingResult result = pricingService.calculate(items, List.of(flashSale), save5, null);

    assertThat(result.subtotalCents()).isEqualTo(SeedCatalog.HERO_STANDARD_CENTS);
    assertThat(result.discountCents())
        .isEqualTo(SeedCatalog.DEMO_ORDER_FLASH_DISCOUNT_CENTS + SeedCatalog.SAVE5_CENTS);
    assertThat(result.totalCents()).isEqualTo(SeedCatalog.DEMO_ORDER_TOTAL_CENTS);
    assertThat(SeedCatalog.DEMO_ORDER_TOTAL_KHR).isEqualTo(508_400L);
  }

  @Test
  @DisplayName("10. Premium variant in cart -> no flash discount, full Premium price")
  void testPremiumVariantNoFlashDiscount() {
    // Hero Royal Blue Premium: price 19900c, not on flash sale (flashSalePrice = null)
    List<PricingItem> items =
        List.of(
            new PricingItem(
                SeedCatalog.HERO_ID,
                SeedCatalog.HERO_BLUE_PREMIUM,
                SeedCatalog.HERO_PREMIUM_CENTS,
                1,
                null,
                false,
                null));

    Promotion flashSale =
        Promotion.builder().name("Flash Sale").type(PromotionType.FLASH_SALE).active(true).build();

    PricingResult result = pricingService.calculate(items, List.of(flashSale), null, null);

    assertThat(result.subtotalCents()).isEqualTo(SeedCatalog.HERO_PREMIUM_CENTS);
    assertThat(result.discountCents()).isEqualTo(0L);
    assertThat(result.totalCents()).isEqualTo(SeedCatalog.HERO_PREMIUM_CENTS);
    assertThat(result.promotionDiscounts()).isEmpty();
  }

  @Test
  @DisplayName("11. Standard variant in cart -> flash discount applies, total = flash price")
  void testStandardVariantFlashDiscount() {
    // Hero Ruby Red Standard: price 14900c, flash sale price 12900c
    List<PricingItem> items =
        List.of(
            new PricingItem(
                SeedCatalog.HERO_ID,
                SeedCatalog.HERO_RED_STANDARD,
                SeedCatalog.HERO_STANDARD_CENTS,
                1,
                SeedCatalog.HERO_FLASH_CENTS,
                false,
                null));

    Promotion flashSale =
        Promotion.builder().name("Flash Sale").type(PromotionType.FLASH_SALE).active(true).build();

    PricingResult result = pricingService.calculate(items, List.of(flashSale), null, null);

    assertThat(result.subtotalCents()).isEqualTo(SeedCatalog.HERO_STANDARD_CENTS);
    assertThat(result.discountCents()).isEqualTo(SeedCatalog.DEMO_ORDER_FLASH_DISCOUNT_CENTS);
    assertThat(result.totalCents()).isEqualTo(SeedCatalog.HERO_FLASH_CENTS);
    assertThat(result.promotionDiscounts()).hasSize(1);
    assertThat(result.promotionDiscounts().get(0).label()).isEqualTo("Flash Sale Discount");
  }

  @Test
  @DisplayName("12. Mixed cart (Standard + Premium) -> only the Standard variant is discounted")
  void testMixedCartOnlyStandardDiscounted() {
    long standard = SeedCatalog.HERO_STANDARD_CENTS;
    long premium = SeedCatalog.HERO_PREMIUM_CENTS;
    long flashDiscount = SeedCatalog.DEMO_ORDER_FLASH_DISCOUNT_CENTS;
    List<PricingItem> items =
        List.of(
            new PricingItem(
                SeedCatalog.HERO_ID,
                SeedCatalog.HERO_RED_STANDARD,
                standard,
                1,
                SeedCatalog.HERO_FLASH_CENTS,
                false,
                null),
            new PricingItem(
                SeedCatalog.HERO_ID, SeedCatalog.HERO_BLUE_PREMIUM, premium, 1, null, false, null));

    Promotion flashSale =
        Promotion.builder().name("Flash Sale").type(PromotionType.FLASH_SALE).active(true).build();

    PricingResult result = pricingService.calculate(items, List.of(flashSale), null, null);

    assertThat(result.subtotalCents()).isEqualTo(standard + premium);
    assertThat(result.discountCents()).isEqualTo(flashDiscount); // only the Standard line
    assertThat(result.totalCents()).isEqualTo(standard + premium - flashDiscount);
    assertThat(result.promotionDiscounts()).hasSize(1);
    assertThat(result.promotionDiscounts().get(0).label()).isEqualTo("Flash Sale Discount");
    assertThat(result.promotionDiscounts().get(0).amountCents()).isEqualTo(flashDiscount);
  }

  @Test
  @DisplayName(
      "13. Two active flash sales on different variants: each line gets its own flash price, once")
  void testTwoActiveFlashSalesOnDifferentVariants() {
    Instant now = Instant.now();
    Promotion flashA =
        Promotion.builder()
            .name("Flash A")
            .type(PromotionType.FLASH_SALE)
            .startsAt(now.minusSeconds(3600))
            .endsAt(now.plusSeconds(3600))
            .active(true)
            .build();
    Promotion flashB =
        Promotion.builder()
            .name("Flash B")
            .type(PromotionType.FLASH_SALE)
            .startsAt(now.minusSeconds(3600))
            .endsAt(now.plusSeconds(7200))
            .active(true)
            .build();

    // Ruby Red Standard: 14900 -> 12900 (flash A); Ruby Red Premium: 19900 -> 17900 (flash B);
    // Royal Blue Premium not on sale
    long standard = SeedCatalog.HERO_STANDARD_CENTS;
    long premium = SeedCatalog.HERO_PREMIUM_CENTS;
    long standardOff = standard - SeedCatalog.HERO_FLASH_CENTS;
    long premiumSale = premium - 2000L;
    List<PricingItem> items =
        List.of(
            new PricingItem(
                SeedCatalog.HERO_ID,
                SeedCatalog.HERO_RED_STANDARD,
                standard,
                1,
                SeedCatalog.HERO_FLASH_CENTS),
            new PricingItem(
                SeedCatalog.HERO_ID, SeedCatalog.HERO_RED_PREMIUM, premium, 2, premiumSale),
            new PricingItem(SeedCatalog.HERO_ID, SeedCatalog.HERO_BLUE_PREMIUM, premium, 1, null));

    PricingResult result = pricingService.calculate(items, List.of(flashA, flashB), null, null);

    assertThat(result.subtotalCents()).isEqualTo(standard + 2 * premium + premium);
    // 2000 (Ruby Red Standard) + 2 * 2000 (Ruby Red Premium); Royal Blue Premium pays full price
    long expectedFlash = standardOff + 2 * (premium - premiumSale);
    assertThat(result.promotionDiscounts()).hasSize(1);
    assertThat(result.promotionDiscounts().get(0).label()).isEqualTo("Flash Sale Discount");
    assertThat(result.promotionDiscounts().get(0).amountCents()).isEqualTo(expectedFlash);
    assertThat(result.totalCents()).isEqualTo(result.subtotalCents() - expectedFlash);
  }

  private Promotion promo(PromotionType type, Integer percent) {
    return Promotion.builder()
        .name(type.name())
        .type(type)
        .discountPercent(percent)
        .active(true)
        .build();
  }

  @Test
  @DisplayName("14. Best wins per line: seasonal 20% beats a smaller flash discount, never both")
  void testSeasonalBeatsSmallerFlash() {
    // 14900c, flash price 12900c (2000 off) but also in seasonal 20% (2980 off) -> seasonal only
    long standard = SeedCatalog.HERO_STANDARD_CENTS;
    List<PricingItem> items =
        List.of(
            new PricingItem(
                SeedCatalog.HERO_ID,
                SeedCatalog.HERO_RED_STANDARD,
                standard,
                1,
                SeedCatalog.HERO_FLASH_CENTS,
                false,
                20));

    PricingResult result =
        pricingService.calculate(
            items,
            List.of(promo(PromotionType.FLASH_SALE, null), promo(PromotionType.SEASONAL, 20)),
            null,
            null);

    assertThat(result.promotionDiscounts()).hasSize(1);
    assertThat(result.promotionDiscounts().get(0).label()).contains("Seasonal");
    assertThat(result.promotionDiscounts().get(0).amountCents()).isEqualTo(standard * 20 / 100);
    assertThat(result.totalCents()).isEqualTo(standard - standard * 20 / 100);
  }

  @Test
  @DisplayName(
      "15. Best wins per line: flash beats a smaller seasonal %, BOGO beats both when bigger")
  void testBestOfFlashSeasonalBogoPerLine() {
    // Line A: 10000c, flash 5000c (5000 off) vs seasonal 10% (1000 off) -> flash
    // Line B: 10000c x2, BOGO (10000 off) vs seasonal 10% (2000 off) -> BOGO
    // Line C: 10000c x1, seasonal 10% only (1000 off) -> seasonal
    List<PricingItem> items =
        List.of(
            new PricingItem(1L, null, 10000L, 1, 5000L, false, 10),
            new PricingItem(2L, null, 10000L, 2, null, true, 10),
            new PricingItem(3L, null, 10000L, 1, null, false, 10));

    PricingResult result =
        pricingService.calculate(
            items,
            List.of(
                promo(PromotionType.FLASH_SALE, null),
                promo(PromotionType.SEASONAL, 10),
                promo(PromotionType.BOGO, null)),
            null,
            null);

    assertThat(result.promotionDiscounts())
        .extracting(d -> d.label() + "=" + d.amountCents())
        .containsExactly(
            "Flash Sale Discount=5000", "Seasonal Promotion (10%)=1000", "Buy 1 Get 1 Free=10000");
    assertThat(result.discountCents()).isEqualTo(16000L);
  }

  @Test
  @DisplayName("16. Best wins: equal discounts tie-break flash, then seasonal, then BOGO")
  void testTieBreakPrefersFlash() {
    // 10000c: flash 9000c (1000 off) == seasonal 10% (1000 off) -> counted once, as flash
    List<PricingItem> items = List.of(new PricingItem(1L, null, 10000L, 1, 9000L, false, 10));

    PricingResult result =
        pricingService.calculate(
            items,
            List.of(promo(PromotionType.FLASH_SALE, null), promo(PromotionType.SEASONAL, 10)),
            null,
            null);

    assertThat(result.promotionDiscounts()).hasSize(1);
    assertThat(result.promotionDiscounts().get(0).label()).isEqualTo("Flash Sale Discount");
    assertThat(result.discountCents()).isEqualTo(1000L);
  }

  @Test
  @DisplayName(
      "17. BOGO on Lemongrass Soap: 2 x 1033 -> one unit free (-350); 1 unit -> no discount")
  void testBogoLemongrassSoap() {
    long price = SeedCatalog.BOGO_PRODUCT_CENTS;
    long id = SeedCatalog.BOGO_PRODUCT_ID;
    List<Promotion> promotions =
        List.of(promo(PromotionType.BOGO, null), promo(PromotionType.SEASONAL, 10));

    // 2 units: BOGO frees one (350) and beats a 10% seasonal (70) on the same line
    PricingResult two =
        pricingService.calculate(
            List.of(new PricingItem(id, null, price, 2, null, true, 10)), promotions, null, null);
    assertThat(two.subtotalCents()).isEqualTo(2 * price);
    assertThat(two.discountCents()).isEqualTo(price);
    assertThat(two.totalCents()).isEqualTo(price);
    assertThat(two.promotionDiscounts())
        .extracting(d -> d.label() + "=" + d.amountCents())
        .containsExactly("Buy 1 Get 1 Free=" + price);

    // 1 unit: BOGO needs a pair, so only the seasonal percent could apply
    PricingResult one =
        pricingService.calculate(
            List.of(new PricingItem(id, null, price, 1, null, true, 10)), promotions, null, null);
    assertThat(one.promotionDiscounts()).hasSize(1);
    assertThat(one.promotionDiscounts().get(0).label()).contains("Seasonal");
  }
}
