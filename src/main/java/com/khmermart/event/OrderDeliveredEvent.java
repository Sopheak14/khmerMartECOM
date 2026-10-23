package com.khmermart.event;

public record OrderDeliveredEvent(
    Long orderId, String orderNumber, Long userId, String email, String fullName, String phone) {}
