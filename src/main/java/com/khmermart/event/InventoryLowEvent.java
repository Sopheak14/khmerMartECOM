package com.khmermart.event;

public record InventoryLowEvent(
    Long productId, String productName, String sku, int available, int threshold) {}
