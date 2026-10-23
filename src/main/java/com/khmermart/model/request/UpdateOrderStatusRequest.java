package com.khmermart.model.request;

import com.khmermart.model.enumerate.OrderStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateOrderStatusRequest(
    @NotNull(message = "Order status is required") OrderStatus status, String note) {}
