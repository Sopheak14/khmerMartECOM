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

    List<PromotionDiscountDto> promotionDiscounts = new ArrayList<>();
    long autoDiscountCents = 0L;

    // 2. Automatic promotions (applied first)
    if (promotions != null) {
      for (Promotion promo : promotions) {
        if (!promo.isActive()) {
          continue;
        }

        if (promo.getType() == PromotionType.FLASH_SALE) {
          long flashDiscount = 0L;
          for (PricingItem item : items) {
            if (item.flashSalePriceCents() != null
                && item.flashSalePriceCents() < item.unitPriceCents()) {
              flashDiscount +=
                  (item.unitPriceCents() - item.flashSalePriceCents()) * item.quantity();
            }
          }
          if (flashDiscount > 0) {
            autoDiscountCents += flashDiscount;
            promotionDiscounts.add(new PromotionDiscountDto("Flash Sale Discount", flashDiscount));
          }
        } else if (promo.getType() == PromotionType.BOGO) {
          long bogoDiscount = 0L;
          for (PricingItem item : items) {
            if (item.bogoEligible() && item.quantity() >= 2) {
              int freeUnits = item.quantity() / 2;
              bogoDiscount += freeUnits * item.unitPriceCents();
            }
          }
          if (bogoDiscount > 0) {
            autoDiscountCents += bogoDiscount;
            promotionDiscounts.add(new PromotionDiscountDto("Buy 1 Get 1 Free", bogoDiscount));
          }
        } else if (promo.getType() == PromotionType.SEASONAL
            && promo.getDiscountPercent() != null
            && promo.getDiscountPercent() > 0) {
          if (promo.getThresholdCents() == null || subtotalCents >= promo.getThresholdCents()) {
            long seasonalDiscount =
                BigDecimal.valueOf(subtotalCents)
                    .multiply(BigDecimal.valueOf(promo.getDiscountPercent()))
                    .divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP)
                    .longValue();
            if (seasonalDiscount > 0) {
              autoDiscountCents += seasonalDiscount;
              promotionDiscounts.add(
                  new PromotionDiscountDto(
                      "Seasonal Promotion (" + promo.getDiscountPercent() + "%)",
                      seasonalDiscount));
            }
          }
        }
      }
    }

    autoDiscountCents = Math.min(autoDiscountCents, subtotalCents);
    long remainingSubtotal = Math.max(0L, subtotalCents - autoDiscountCents);

    // 3. Coupon discount (at most one coupon)
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
              && (subtotalCents - totalDiscountCents) >= shippingMethod.getFreeOverCents();

      boolean freeShippingPromo = false;
      if (promotions != null) {
        for (Promotion promo : promotions) {
          if (promo.isActive()
              && promo.getType() == PromotionType.FREE_SHIPPING_THRESHOLD
              && promo.getThresholdCents() != null
              && subtotalCents >= promo.getThresholdCents()) {
            freeShippingPromo = true;
            break;
          }
        }
      }

      if (freeShippingCoupon || orderOverThreshold || freeShippingPromo) {
        shippingCents = 0L;
      } else {
        shippingCents = baseFee;
      }
    }

    // 5. Total cents (floor at 0)
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
