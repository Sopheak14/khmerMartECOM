package com.khmermart.model.request;

import java.util.List;

public record AdminProductUpdateRequest(
    String name,
    String nameKm,
    String sku,
    String slug,
    String description,
    String brand,
    Long priceCents,
    Long compareAtCents,
    Long categoryId,
    Boolean active,
    List<AdminProductCreateRequest.AdminVariantRequest> variants) {}
