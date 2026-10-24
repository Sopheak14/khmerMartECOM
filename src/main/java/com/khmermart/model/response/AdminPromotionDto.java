package com.khmermart.model.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.khmermart.model.enumerate.PromotionType;
import java.time.Instant;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record AdminPromotionDto(
    Long id,
    String name,
    String nameKm,
    PromotionType type,
    Integer discountPercent,
    Long thresholdCents,
    Instant startsAt,
    Instant endsAt,
    boolean active,
    List<Long> productIds) {}
