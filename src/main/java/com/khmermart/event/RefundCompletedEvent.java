package com.khmermart.event;

public record RefundCompletedEvent(
    String orderNumber,
    Long userId,
    String email,
    String fullName,
    long amountCents,
    String payerAccount,
    String bankReference) {}
