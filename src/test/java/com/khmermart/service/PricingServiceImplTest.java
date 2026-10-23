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
      "4. Shipping rules: standard fee waived when subtotal >= freeOverCents or with FREESHIP coupon")
  void testFreeShipping() {
    ShippingMethod standard =
        ShippingMethod.builder().code("STANDARD").feeCents(200L).freeOverCents(5000L).build();

    // Below threshold, no coupon -> shipping fee 200
    List<PricingItem> smallOrder = List.of(new PricingItem(1L, null, 3000L, 1, null));
    PricingResult res1 = pricingService.calculate(smallOrder, List.of(), null, standard);
    assertThat(res1.shippingCents()).isEqualTo(200L);
    assertThat(res1.totalCents()).isEqualTo(3200L);

    // Above threshold ($50 / 5000c) -> shipping fee 0
    List<PricingItem> largeOrder = List.of(new PricingItem(1L, null, 6000L, 1, null));
    PricingResult res2 = pricingService.calculate(largeOrder, List.of(), null, standard);
    assertThat(res2.shippingCents()).isEqualTo(0L);
    assertThat(res2.totalCents()).isEqualTo(6000L);

    // Below threshold with FREESHIP coupon -> shipping fee 0
    Coupon freeShipCoupon =
        Coupon.builder()
            .code("FREESHIP")
            .type(CouponType.FREE_SHIPPING)
            .value(0L)
            .active(true)
            .build();
    PricingResult res3 = pricingService.calculate(smallOrder, List.of(), freeShipCoupon, standard);
    assertThat(res3.shippingCents()).isEqualTo(0L);
    assertThat(res3.totalCents()).isEqualTo(3000L);
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
}
