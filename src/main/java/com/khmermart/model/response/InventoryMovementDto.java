package com.khmermart.model.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.khmermart.model.enumerate.InventoryMovementType;
import java.time.Instant;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record InventoryMovementDto(
    Long id,
    Long inventoryId,
    InventoryMovementType type,
    int quantity,
    String reason,
    Long refOrderId,
    Long actorId,
    Instant createdAt) {}
