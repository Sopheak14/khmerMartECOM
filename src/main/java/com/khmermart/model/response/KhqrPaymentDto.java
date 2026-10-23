package com.khmermart.model.response;

import com.khmermart.model.enumerate.PaymentStatus;
import java.time.Instant;

public record KhqrPaymentDto(
    Long paymentId,
    String orderNumber,
    PaymentStatus status,
    String method,
    String currency,
    Double amount,
    Long amountCents,
    String qrString,
    String qrImageDataUrl,
    String md5,
    Instant expiresAt,
    String merchantName,
    String deepLink,
    String mode,
    String streamToken) {}
