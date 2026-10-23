package com.khmermart.model.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;

@JsonIgnoreProperties(ignoreUnknown = true)
public record BakongTxData(
    String hash,
    String fromAccountId,
    String toAccountId,
    String currency,
    BigDecimal amount,
    String description,
    Long createdDateMs,
    Long acknowledgedDateMs,
    String externalRef) {}
