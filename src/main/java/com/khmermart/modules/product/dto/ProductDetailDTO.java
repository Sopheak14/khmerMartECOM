package com.khmermart.modules.product.dto;

import com.khmermart.modules.category.dto.CategoryDTO;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public record ProductDetailDTO(
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
    CategoryDTO category,
    List<ImageDTO> images,
    List<VariantDTO> variants,
    Map<String, List<String>> variantOptions,
    Map<String, Long> ratingBreakdown,
    Instant flashSaleEndsAt,
    List<ProductCardDTO> related) {}
