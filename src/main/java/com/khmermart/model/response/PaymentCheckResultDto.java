package com.khmermart.model.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.khmermart.model.enumerate.PaymentStatus;

/** Result of a single Bakong check for one payment. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PaymentCheckResultDto(
    Long paymentId,
    Outcome outcome,
    String reason,
    Integer bakongResponseCode,
    PaymentStatus paymentStatus,
    int checkCount) {

  public enum Outcome {
    /** Bakong confirmed it and the payment is now PAID. */
    PAID,
    /** Bakong confirmed it after expiry: recorded as PAID, order flagged for admin attention. */
    LATE_PAID,
    /** Payment was already PAID; nothing to do. */
    ALREADY_PAID,
    /** Bakong has no matching transaction (yet). */
    NOT_FOUND,
    /** Bakong found a transaction but it does not match this payment (amount/currency/account). */
    REJECTED,
    /** Could not get a usable answer from Bakong (network, auth, 403 outside Cambodia...). */
    BAKONG_ERROR,
    /** Not a checkable payment (not KHQR, no md5, or terminal status). */
    SKIPPED
  }
}
