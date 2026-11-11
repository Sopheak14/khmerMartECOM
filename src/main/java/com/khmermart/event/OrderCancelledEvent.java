package com.khmermart.event;

/**
 * Order was cancelled. When {@code refundPending} is true a person still has to send {@code
 * refundAmountCents} back to {@code payerAccount} by bank transfer.
 */
public record OrderCancelledEvent(
    Long orderId,
    String orderNumber,
    Long userId,
    String email,
    String fullName,
    String phone,
    String reason,
    boolean refundPending,
    Long refundAmountCents,
    String payerAccount) {

  public OrderCancelledEvent(
      Long orderId,
      String orderNumber,
      Long userId,
      String email,
      String fullName,
      String phone,
      String reason) {
    this(orderId, orderNumber, userId, email, fullName, phone, reason, false, null, null);
  }
}
