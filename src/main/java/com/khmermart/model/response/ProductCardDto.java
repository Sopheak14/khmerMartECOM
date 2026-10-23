package com.khmermart.model.response;

import java.math.BigDecimal;
import java.util.List;

public record ProductCardDto(
    Long id,
    String sku,
    String slug,
    String name,
    String nameKm,
    String brand,
    Long priceCents,
    Long compareAtCents,
    Long salePriceCents,
    String imageUrl,
    BigDecimal avgRating,
    int reviewCount,
    int available,
    boolean inStock,
    List<String> badges) {}
