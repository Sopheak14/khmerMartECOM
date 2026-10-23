package com.khmermart.model.response;

import java.util.Map;

public record VariantDto(
    Long id, String sku, Map<String, String> attributes, Long priceCents, int available) {}
