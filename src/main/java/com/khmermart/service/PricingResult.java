package com.khmermart.service;

import com.khmermart.model.response.CouponInfoDto;
import com.khmermart.model.response.PromotionDiscountDto;
import java.util.List;

public record PricingResult(
    Long subtotalCents,
    Long discountCents,
    List<PromotionDiscountDto> promotionDiscounts,
    CouponInfoDto coupon,
    Long shippingCents,
    Long totalCents) {}
