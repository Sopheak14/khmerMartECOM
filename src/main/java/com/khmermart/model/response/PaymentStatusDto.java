package com.khmermart.model.response;

import com.khmermart.model.enumerate.OrderStatus;
import com.khmermart.model.enumerate.PaymentStatus;
import java.time.Instant;

public record PaymentStatusDto(
    Long paymentId,
    PaymentStatus status,
    OrderStatus orderStatus,
    Instant paidAt,
    String bakongHash,
    Instant expiresAt) {}
