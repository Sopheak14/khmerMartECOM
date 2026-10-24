package com.khmermart.model.request;

import jakarta.validation.constraints.NotNull;

public record InventoryAdjustRequest(
    @NotNull(message = "Quantity is required") Integer quantity, String reason) {}
