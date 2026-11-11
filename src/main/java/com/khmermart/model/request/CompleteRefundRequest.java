package com.khmermart.model.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CompleteRefundRequest(
    @NotBlank(message = "bankReference is required")
        @Size(min = 3, max = 64, message = "bankReference must be 3-64 characters")
        String bankReference,
    @Size(max = 500, message = "note max 500 chars") String note) {}
