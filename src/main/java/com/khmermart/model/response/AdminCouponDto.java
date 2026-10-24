package com.khmermart.model.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.khmermart.model.enumerate.CouponType;
import java.time.Instant;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record AdminCouponDto(
    Long id,
    String code,
    CouponType type,
    Long value,
    String description,
    Long minOrderCents,
    Long maxDiscountCents,
    Integer maxUses,
    int usedCount,
    Instant startsAt,
    Instant endsAt,
    boolean active) {}
