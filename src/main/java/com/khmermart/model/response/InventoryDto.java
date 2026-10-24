package com.khmermart.model.response;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record InventoryDto(
    Long id,
    Long productId,
    String productName,
    String sku,
    Long variantId,
    int quantityOnHand,
    int quantityReserved,
    int available,
    int lowStockThreshold,
    boolean lowStockAlerted) {}
