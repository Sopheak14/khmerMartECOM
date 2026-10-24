package com.khmermart.model.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record RefundRequest(
    @NotNull(message = "amountCents is required")
        @Positive(message = "amountCents must be positive")
        Long amountCents,
    String reason) {}
