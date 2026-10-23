package com.khmermart.model.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record PushSubscriptionRequest(
    @NotBlank(message = "Endpoint is required") String endpoint,
    @NotNull(message = "Keys are required") @Valid PushKeysRequest keys) {}
