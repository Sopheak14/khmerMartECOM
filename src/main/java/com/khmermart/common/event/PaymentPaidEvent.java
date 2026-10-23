package com.khmermart.common.event;

public record PaymentPaidEvent(Long paymentId, Long orderId, Long amountCents, String bakongHash) {}
