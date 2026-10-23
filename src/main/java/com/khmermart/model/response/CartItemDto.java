package com.khmermart.model.response;

import java.util.List;
import java.util.Map;

public record CartItemDto(
    Long id,
    Long productId,
    Long variantId,
    String name,
    String nameKm,
    String imageUrl,
    String slug,
    Map<String, String> attributes,
    Long unitPriceCents,
    int quantity,
    Long lineTotalCents,
    int available,
    List<String> issues) {}
