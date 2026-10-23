package com.khmermart.common.event;

import com.khmermart.model.enumerate.OrderStatus;
import com.khmermart.model.enumerate.PaymentStatus;
import java.time.Instant;

public record PaymentStatusChangedEvent(
    Long paymentId,
    Long orderId,
    PaymentStatus status,
    OrderStatus orderStatus,
    Instant paidAt,
    String bakongHash,
    Instant expiresAt) {}
