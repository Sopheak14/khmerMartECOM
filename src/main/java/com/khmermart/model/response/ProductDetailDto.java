package com.khmermart.model.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public record ProductDetailDto(
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
    List<String> badges,
    String description,
    CategoryDto category,
    List<ImageDto> images,
    List<VariantDto> variants,
    Map<String, List<String>> variantOptions,
    Map<String, Long> ratingBreakdown,
    Instant flashSaleEndsAt,
    List<ProductCardDto> related) {}
