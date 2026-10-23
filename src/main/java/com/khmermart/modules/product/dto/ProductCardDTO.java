package com.khmermart.modules.product.dto;

import java.math.BigDecimal;
import java.util.List;

public record ProductCardDTO(
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
