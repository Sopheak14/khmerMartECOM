package com.khmermart.event;

public record ReviewRepliedEvent(
    Long reviewId,
    Long productId,
    String productName,
    Long userId,
    String email,
    String fullName,
    String adminReply) {}
