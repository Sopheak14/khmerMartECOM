package com.khmermart.model.response;

import com.khmermart.model.enumerate.ReviewStatus;
import java.time.Instant;

public record ReviewDto(
    Long id,
    Long productId,
    ReviewUserDto user,
    int rating,
    String title,
    String body,
    boolean verifiedPurchase,
    ReviewStatus status,
    String adminReply,
    Instant createdAt) {}
