package com.khmermart.model.request;

import com.khmermart.model.enumerate.PromotionType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;

public record AdminPromotionRequest(
    @NotBlank(message = "Promotion name is required") String name,
    String nameKm,
    @NotNull(message = "Promotion type is required") PromotionType type,
    Integer discountPercent,
    Long thresholdCents,
    @NotNull(message = "startsAt is required") Instant startsAt,
    @NotNull(message = "endsAt is required") Instant endsAt,
    Boolean active,
    @Deprecated List<Long> productIds,
    @Valid List<PromotionItemRequest> items) {}
