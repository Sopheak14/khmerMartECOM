package com.khmermart.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.khmermart.model.entity.Coupon;
import com.khmermart.model.entity.Promotion;
import com.khmermart.model.entity.ShippingMethod;
import com.khmermart.model.enumerate.CouponType;
import com.khmermart.model.enumerate.PromotionType;
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
  @DisplayName("7. Laptop Pro 15 in flash sale + seasonal -> only the flash sale applies (89900)")
  void testFlashSaleWithActiveSeasonalPromo() {
    // Laptop Pro 15: price 99900c, flash sale price 89900c (not linked to seasonal)
    List<PricingItem> items = List.of(new PricingItem(1001L, 1L, 99900L, 1, 89900L, false, null));

    Promotion flashSale =
        Promotion.builder().name("Flash Sale").type(PromotionType.FLASH_SALE).active(true).build();

    Promotion seasonal =
        Promotion.builder()
            .name("Seasonal 20%")
            .type(PromotionType.SEASONAL)
            .discountPercent(20)
            .thresholdCents(5000L)
            .active(true)
            .build();

    PricingResult result =
        pricingService.calculate(items, List.of(flashSale, seasonal), null, null);

    assertThat(result.subtotalCents()).isEqualTo(99900L);
    assertThat(result.discountCents()).isEqualTo(10000L); // 99900 - 89900
    assertThat(result.totalCents()).isEqualTo(89900L);
    assertThat(result.promotionDiscounts()).hasSize(1);
    assertThat(result.promotionDiscounts().get(0).label()).isEqualTo("Flash Sale Discount");
  }

  @Test
  @DisplayName("8. Seasonal-only product -> 20% off its price")
  void testSeasonalOnlyProduct() {
    // Product 1002 (Dell Latitude 5520, $750 = 75000c) with seasonal 20%
    List<PricingItem> items = List.of(new PricingItem(1002L, 5L, 75000L, 1, null, false, 20));

    Promotion seasonal =
        Promotion.builder()
            .name("Seasonal 20%")
            .type(PromotionType.SEASONAL)
            .discountPercent(20)
            .thresholdCents(5000L)
            .active(true)
            .build();

    PricingResult result = pricingService.calculate(items, List.of(seasonal), null, null);

    assertThat(result.subtotalCents()).isEqualTo(75000L);
    assertThat(result.discountCents()).isEqualTo(15000L); // 20% of 75000
    assertThat(result.totalCents()).isEqualTo(60000L);
    assertThat(result.promotionDiscounts()).hasSize(1);
    assertThat(result.promotionDiscounts().get(0).label()).contains("Seasonal");
  }

  @Test
  @DisplayName("9. Flash item + SAVE5 -> 89900 - 500 = 89400")
  void testFlashSaleItemWithSave5Coupon() {
    // Laptop Pro 15 (99900c, flash price 89900c) + coupon SAVE5 (500c)
    List<PricingItem> items = List.of(new PricingItem(1001L, 1L, 99900L, 1, 89900L, false, null));

    Promotion flashSale =
        Promotion.builder().name("Flash Sale").type(PromotionType.FLASH_SALE).active(true).build();

    Coupon save5 =
        Coupon.builder()
            .code("SAVE5")
            .type(CouponType.FIXED)
            .value(500L)
            .minOrderCents(3500L)
            .active(true)
            .build();

    PricingResult result = pricingService.calculate(items, List.of(flashSale), save5, null);

    assertThat(result.subtotalCents()).isEqualTo(99900L);
    assertThat(result.discountCents()).isEqualTo(10500L); // 10000 (flash) + 500 (coupon)
    assertThat(result.totalCents()).isEqualTo(89400L); // 89900 - 500
  }

  @Test
  @DisplayName("10. Variant 3 in cart -> no flash discount, total 129900")
  void testVariant3NoFlashDiscount() {
    // Laptop Pro 15 Variant 3: price 129900c, not on flash sale (flashSalePrice = null)
    List<PricingItem> items = List.of(new PricingItem(1001L, 3L, 129900L, 1, null, false, null));

    Promotion flashSale =
        Promotion.builder().name("Flash Sale").type(PromotionType.FLASH_SALE).active(true).build();

    PricingResult result = pricingService.calculate(items, List.of(flashSale), null, null);

    assertThat(result.subtotalCents()).isEqualTo(129900L);
    assertThat(result.discountCents()).isEqualTo(0L);
    assertThat(result.totalCents()).isEqualTo(129900L);
    assertThat(result.promotionDiscounts()).isEmpty();
  }

  @Test
  @DisplayName("11. Variant 1 in cart -> flash discount applies, total 89900")
  void testVariant1FlashDiscount() {
    // Laptop Pro 15 Variant 1: price 99900c, flash sale price 89900c
    List<PricingItem> items = List.of(new PricingItem(1001L, 1L, 99900L, 1, 89900L, false, null));

    Promotion flashSale =
        Promotion.builder().name("Flash Sale").type(PromotionType.FLASH_SALE).active(true).build();

    PricingResult result = pricingService.calculate(items, List.of(flashSale), null, null);

    assertThat(result.subtotalCents()).isEqualTo(99900L);
    assertThat(result.discountCents()).isEqualTo(10000L);
    assertThat(result.totalCents()).isEqualTo(89900L);
    assertThat(result.promotionDiscounts()).hasSize(1);
    assertThat(result.promotionDiscounts().get(0).label()).isEqualTo("Flash Sale Discount");
  }

  @Test
  @DisplayName("12. Mixed cart (Variant 1 + Variant 3) -> only Variant 1 is discounted")
  void testMixedCartOnlyVariant1Discounted() {
    List<PricingItem> items =
        List.of(
            new PricingItem(1001L, 1L, 99900L, 1, 89900L, false, null),
            new PricingItem(1001L, 3L, 129900L, 1, null, false, null));

    Promotion flashSale =
        Promotion.builder().name("Flash Sale").type(PromotionType.FLASH_SALE).active(true).build();

    PricingResult result = pricingService.calculate(items, List.of(flashSale), null, null);

    assertThat(result.subtotalCents()).isEqualTo(99900L + 129900L); // 229800
    assertThat(result.discountCents()).isEqualTo(10000L); // only variant 1 discount
    assertThat(result.totalCents()).isEqualTo(219800L);
    assertThat(result.promotionDiscounts()).hasSize(1);
    assertThat(result.promotionDiscounts().get(0).label()).isEqualTo("Flash Sale Discount");
    assertThat(result.promotionDiscounts().get(0).amountCents()).isEqualTo(10000L);
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

    // Variant 1: 99900 -> 89900 (flash A); variant 3: 129900 -> 119900 (flash B); variant 4 not on
    // sale
    List<PricingItem> items =
        List.of(
            new PricingItem(1001L, 1L, 99900L, 1, 89900L),
            new PricingItem(1001L, 3L, 129900L, 2, 119900L),
            new PricingItem(1001L, 4L, 129900L, 1, null));

    PricingResult result = pricingService.calculate(items, List.of(flashA, flashB), null, null);

    assertThat(result.subtotalCents()).isEqualTo(99900L + 2 * 129900L + 129900L);
    // 10000 (variant 1) + 2 * 10000 (variant 3); variant 4 pays full price
    assertThat(result.promotionDiscounts()).hasSize(1);
    assertThat(result.promotionDiscounts().get(0).label()).isEqualTo("Flash Sale Discount");
    assertThat(result.promotionDiscounts().get(0).amountCents()).isEqualTo(30000L);
    assertThat(result.totalCents()).isEqualTo(result.subtotalCents() - 30000L);
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
    // 99900c, flash price 89900c (10000 off) but also in seasonal 20% (19980 off) -> seasonal only
    List<PricingItem> items = List.of(new PricingItem(1001L, 1L, 99900L, 1, 89900L, false, 20));

    PricingResult result =
        pricingService.calculate(
            items,
            List.of(promo(PromotionType.FLASH_SALE, null), promo(PromotionType.SEASONAL, 20)),
            null,
            null);

    assertThat(result.promotionDiscounts()).hasSize(1);
    assertThat(result.promotionDiscounts().get(0).label()).contains("Seasonal");
    assertThat(result.promotionDiscounts().get(0).amountCents()).isEqualTo(19980L);
    assertThat(result.totalCents()).isEqualTo(99900L - 19980L);
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
}
