package com.khmermart.model.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.khmermart.model.enumerate.PaymentMethod;
import com.khmermart.model.enumerate.PaymentStatus;
import java.time.Instant;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record AdminPaymentDto(
    Long id,
    Long orderId,
    String orderNumber,
    PaymentMethod method,
    PaymentStatus status,
    Long amountCents,
    String currency,
    Long amountKhr,
    String khqrMd5,
    String bakongHash,
    String fromAccountId,
    String toAccountId,
    String failureReason,
    Instant paidAt,
    JsonNode rawResponse,
    Instant createdAt,
    String externalRef,
    Integer checkCount,
    Instant lastCheckedAt,
    Instant qrExpiresAt,
    Boolean orderNeedsAttention,
    String orderAttentionNote) {}
