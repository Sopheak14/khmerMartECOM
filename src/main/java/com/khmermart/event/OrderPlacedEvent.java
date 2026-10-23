package com.khmermart.event;

public record OrderPlacedEvent(
    Long orderId,
    String orderNumber,
    Long userId,
    String email,
    String fullName,
    String phone,
    Long totalCents,
    Long subtotalCents,
    Long shippingCents,
    String couponCode) {}
