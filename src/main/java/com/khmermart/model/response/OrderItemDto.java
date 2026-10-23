package com.khmermart.model.response;

import java.util.Map;

public record OrderItemDto(
    Long id,
    Long productId,
    String slug,
    String name,
    String imageUrl,
    String sku,
    Map<String, String> attributes,
    Long unitPriceCents,
    int quantity,
    Long lineTotalCents,
    boolean canReview) {}
