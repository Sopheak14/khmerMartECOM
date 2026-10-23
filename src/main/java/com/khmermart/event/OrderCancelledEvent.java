package com.khmermart.event;

public record OrderCancelledEvent(
    Long orderId,
    String orderNumber,
    Long userId,
    String email,
    String fullName,
    String phone,
    String reason) {}
