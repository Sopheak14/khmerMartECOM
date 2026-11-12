package com.khmermart.model.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;

/** Compact refund state embedded in an order. status: PENDING | COMPLETED. */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record RefundInfoDto(
    String status,
    Long amountCents,
    String payerAccount,
    String bankReference,
    Instant completedAt) {}
