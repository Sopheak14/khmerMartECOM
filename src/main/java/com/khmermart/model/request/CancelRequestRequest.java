package com.khmermart.model.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CancelRequestRequest(
    @NotBlank(message = "reason is required") @Size(max = 500, message = "reason max 500 chars")
        String reason) {}
