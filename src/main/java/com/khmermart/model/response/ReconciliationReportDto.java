package com.khmermart.model.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ReconciliationReportDto(
    String status,
    int mismatchCount,
    List<ReconciliationMismatchDto> mismatches,
    Instant generatedAt) {

  public record ReconciliationMismatchDto(
      String type, Long orderId, String orderNumber, Long paymentId, String description) {}
}
