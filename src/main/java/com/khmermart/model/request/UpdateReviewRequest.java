package com.khmermart.model.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

public record UpdateReviewRequest(
    @Min(value = 1, message = "Rating must be at least 1")
        @Max(value = 5, message = "Rating cannot exceed 5")
        Integer rating,
    @Size(max = 255, message = "Title cannot exceed 255 characters") String title,
    String body) {}
