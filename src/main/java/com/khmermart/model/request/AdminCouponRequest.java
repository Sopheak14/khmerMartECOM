package com.khmermart.model.request;

import com.khmermart.model.enumerate.CouponType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

public record AdminCouponRequest(
    @NotBlank(message = "Coupon code is required") String code,
    @NotNull(message = "Coupon type is required") CouponType type,
    @NotNull(message = "Value is required") Long value,
    String description,
    Long minOrderCents,
    Long maxDiscountCents,
    Integer maxUses,
    Instant startsAt,
    Instant endsAt,
    Boolean active) {}
