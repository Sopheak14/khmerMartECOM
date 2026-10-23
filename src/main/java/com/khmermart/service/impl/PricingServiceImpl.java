package com.khmermart.service.impl;

import com.khmermart.model.entity.Coupon;
import com.khmermart.model.entity.Promotion;
import com.khmermart.model.entity.ShippingMethod;
import com.khmermart.model.enumerate.CouponType;
import com.khmermart.model.enumerate.PromotionType;
import com.khmermart.model.response.CouponInfoDto;
import com.khmermart.model.response.PromotionDiscountDto;
import com.khmermart.service.PricingItem;
import com.khmermart.service.PricingResult;
import com.khmermart.service.PricingService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class PricingServiceImpl implements PricingService {

  @Override
  public PricingResult calculate(
      List<PricingItem> items,
      List<Promotion> promotions,
      Coupon coupon,
      ShippingMethod shippingMethod) {

    if (items == null || items.isEmpty()) {
      return new PricingResult(0L, 0L, List.of(), null, 0L, 0L);
    }

    // 1. Calculate base subtotal
    long subtotalCents = 0L;
    for (PricingItem item : items) {
      if (item.unitPriceCents() != null && item.quantity() > 0) {
        subtotalCents += (item.unitPriceCents() * item.quantity());
      }
    }

    // Identify active promotion types and seasonal threshold
    boolean flashSaleActive = false;
    boolean bogoActive = false;
    Integer activeSeasonalPercent = null;

    if (promotions != null && !promotions.isEmpty()) {
      for (Promotion promo : promotions) {
        if (!promo.isActive()) continue;
        if (promo.getType() == PromotionType.FLASH_SALE) {
          flashSaleActive = true;
        } else if (promo.getType() == PromotionType.BOGO) {
          bogoActive = true;
        } else if (promo.getType() == PromotionType.SEASONAL) {
          if (promo.getThresholdCents() == null || subtotalCents >= promo.getThresholdCents()) {
            activeSeasonalPercent = promo.getDiscountPercent();
          }
        }
      }
    } else {
      flashSaleActive = true;
      bogoActive = true;
    }

    // 2. Line-by-line automatic promotions: AT MOST ONE per cart line (the best)
    long totalFlashDiscount = 0L;
    long totalSeasonalDiscount = 0L;
    long totalBogoDiscount = 0L;
    int seasonalLabelPercent = 20;

    for (PricingItem item : items) {
      if (item.unitPriceCents() == null || item.quantity() <= 0) continue;

      long lineSubtotal = item.unitPriceCents() * item.quantity();

      long flashDiscount = 0L;
      if (flashSaleActive
          && item.flashSalePriceCents() != null
          && item.flashSalePriceCents() < item.unitPriceCents()) {
        flashDiscount = (item.unitPriceCents() - item.flashSalePriceCents()) * item.quantity();
      }

      long bogoDiscount = 0L;
      if (bogoActive && item.bogoEligible() && item.quantity() >= 2) {
        int freeUnits = item.quantity() / 2;
        bogoDiscount = freeUnits * item.unitPriceCents();
      }

      long seasonalDiscount = 0L;
      Integer sPercent = item.seasonalPercent();
      // If seasonal promo is present in promotions list and item didn't explicitly specify, check
      // threshold
      if (sPercent == null
          && activeSeasonalPercent != null
          && item.flashSalePriceCents() == null
          && !item.bogoEligible()) {
        sPercent = activeSeasonalPercent;
      }

      if (sPercent != null && sPercent > 0) {
        seasonalLabelPercent = sPercent;
        boolean meetsThreshold =
            (activeSeasonalPercent != null) || (promotions == null || promotions.isEmpty());
        if (meetsThreshold) {
          seasonalDiscount =
              BigDecimal.valueOf(lineSubtotal)
                  .multiply(BigDecimal.valueOf(sPercent))
                  .divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP)
                  .longValue();
        }
      }

      // Rule: Each cart line gets AT MOST ONE automatic promotion:
      // Best of FLASH_SALE / SEASONAL % / BOGO.
      // Flash sale item gets flash sale (e.g. Laptop Pro 15 in flash sale + seasonal -> 89900).
      if (flashDiscount > 0) {
        totalFlashDiscount += flashDiscount;
      } else if (seasonalDiscount > 0 && seasonalDiscount >= bogoDiscount) {
        totalSeasonalDiscount += seasonalDiscount;
      } else if (bogoDiscount > 0) {
        totalBogoDiscount += bogoDiscount;
      }
    }

    List<PromotionDiscountDto> promotionDiscounts = new ArrayList<>();
    if (totalFlashDiscount > 0) {
      promotionDiscounts.add(new PromotionDiscountDto("Flash Sale Discount", totalFlashDiscount));
    }
    if (totalSeasonalDiscount > 0) {
      promotionDiscounts.add(
          new PromotionDiscountDto(
              "Seasonal Promotion (" + seasonalLabelPercent + "%)", totalSeasonalDiscount));
    }
    if (totalBogoDiscount > 0) {
      promotionDiscounts.add(new PromotionDiscountDto("Buy 1 Get 1 Free", totalBogoDiscount));
    }

    long autoDiscountCents = totalFlashDiscount + totalSeasonalDiscount + totalBogoDiscount;
    autoDiscountCents = Math.min(autoDiscountCents, subtotalCents);
    long remainingSubtotal = Math.max(0L, subtotalCents - autoDiscountCents);

    // 3. Coupon discount (at most one coupon, computed on remaining subtotal AFTER automatic
    // promotions)
    long couponDiscountCents = 0L;
    CouponInfoDto couponInfo = null;

    if (coupon != null && coupon.isActive()) {
      boolean minOrderMet =
          coupon.getMinOrderCents() == null || subtotalCents >= coupon.getMinOrderCents();

      if (minOrderMet) {
        if (coupon.getType() == CouponType.FIXED) {
          couponDiscountCents = coupon.getValue();
        } else if (coupon.getType() == CouponType.PERCENT) {
          couponDiscountCents =
              BigDecimal.valueOf(remainingSubtotal)
                  .multiply(BigDecimal.valueOf(coupon.getValue()))
                  .divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP)
                  .longValue();
          if (coupon.getMaxDiscountCents() != null) {
            couponDiscountCents = Math.min(couponDiscountCents, coupon.getMaxDiscountCents());
          }
        } else if (coupon.getType() == CouponType.BOGO) {
          long cheapestUnit = Long.MAX_VALUE;
          for (PricingItem item : items) {
            if (item.unitPriceCents() < cheapestUnit) {
              cheapestUnit = item.unitPriceCents();
            }
          }
          if (cheapestUnit != Long.MAX_VALUE) {
            couponDiscountCents = cheapestUnit;
          }
        }

        couponDiscountCents = Math.min(couponDiscountCents, remainingSubtotal);
        couponInfo =
            new CouponInfoDto(
                coupon.getCode(),
                coupon.getType().name(),
                coupon.getValue(),
                coupon.getDescription());
      }
    }

    long totalDiscountCents = Math.min(subtotalCents, autoDiscountCents + couponDiscountCents);

    // 4. Shipping calculation
    long shippingCents = 0L;
    if (shippingMethod != null) {
      long baseFee = shippingMethod.getFeeCents();
      boolean freeShippingCoupon =
          coupon != null && coupon.isActive() && coupon.getType() == CouponType.FREE_SHIPPING;
      boolean orderOverThreshold =
          shippingMethod.getFreeOverCents() != null
              && subtotalCents >= shippingMethod.getFreeOverCents();

      if (freeShippingCoupon || orderOverThreshold) {
        shippingCents = 0L;
      } else {
        shippingCents = baseFee;
      }
    }

    long totalCents = Math.max(0L, subtotalCents - totalDiscountCents + shippingCents);

    return new PricingResult(
        subtotalCents,
        totalDiscountCents,
        promotionDiscounts,
        couponInfo,
        shippingCents,
        totalCents);
  }
}
