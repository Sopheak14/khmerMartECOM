package com.khmermart.event;

public record CancelRequestRejectedEvent(
    String orderNumber, Long userId, String email, String fullName, String reason) {}
