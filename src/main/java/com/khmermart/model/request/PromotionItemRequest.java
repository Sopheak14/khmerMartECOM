package com.khmermart.model.request;

import jakarta.validation.constraints.NotNull;

public record PromotionItemRequest(
    @NotNull(message = "productId is required") Long productId,
    Long variantId,
    Long salePriceCents) {}
