package com.khmermart.modules.order.model;

public enum OrderStatus {
  PENDING_PAYMENT,
  CONFIRMED,
  PROCESSING,
  SHIPPED,
  OUT_FOR_DELIVERY,
  DELIVERED,
  CANCELLED,
  PAYMENT_EXPIRED,
  REFUNDED
}
