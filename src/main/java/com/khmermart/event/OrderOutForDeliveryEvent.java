package com.khmermart.event;

public record OrderOutForDeliveryEvent(
    Long orderId,
    String orderNumber,
    Long userId,
    String email,
    String fullName,
    String phone,
    String carrier,
    String trackingNumber) {}
