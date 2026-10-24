package com.khmermart.model.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;

public record AdminProductCreateRequest(
    @NotBlank(message = "Product name is required") String name,
    String nameKm,
    String sku,
    String slug,
    String description,
    String brand,
    @NotNull(message = "Price is required") Long priceCents,
    Long compareAtCents,
    Long categoryId,
    Boolean active,
    Integer initialStock,
    List<AdminVariantRequest> variants) {

  public record AdminVariantRequest(
      String sku, Map<String, String> attributes, Long priceCents, Integer stock) {}
}
