package com.khmermart.model.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record RefundDto(
    Long id,
    Long amountCents,
    String payerAccount,
    String reason,
    String status,
    Instant requestedAt,
    Long approvedBy,
    Instant completedAt,
    Long completedBy,
    String bankReference,
    String note) {}
