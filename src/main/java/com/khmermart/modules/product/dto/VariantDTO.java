package com.khmermart.modules.product.dto;

import java.util.Map;

public record VariantDTO(
    Long id, String sku, Map<String, String> attributes, Long priceCents, int available) {}
