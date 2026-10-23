package com.khmermart.model.response;

import java.util.List;

public record CartDto(
    Long id,
    List<CartItemDto> items,
    int itemCount,
    Long subtotalCents,
    Long discountCents,
    List<PromotionDiscountDto> promotionDiscounts,
    CouponInfoDto coupon,
    Long shippingEstimateCents,
    Long totalCents) {}
