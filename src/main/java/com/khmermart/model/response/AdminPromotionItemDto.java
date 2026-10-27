package com.khmermart.model.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record AdminPromotionItemDto(
    Long productId,
    String productName,
    Long variantId,
    String sku,
    Map<String, String> attributes,
    Long priceCents,
    Long salePriceCents) {}
