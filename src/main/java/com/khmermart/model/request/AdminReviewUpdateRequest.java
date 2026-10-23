package com.khmermart.model.request;

import com.khmermart.model.enumerate.ReviewStatus;
import jakarta.validation.constraints.NotNull;

public record AdminReviewUpdateRequest(
    @NotNull(message = "Review status is required") ReviewStatus status, String adminReply) {}
