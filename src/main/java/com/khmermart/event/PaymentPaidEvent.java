package com.khmermart.event;

public record PaymentPaidEvent(
    Long paymentId,
    Long orderId,
    String orderNumber,
    Long userId,
    String email,
    String fullName,
    String phone,
    Long amountCents,
    String bakongHash,
    byte[] invoicePdf) {}
