package com.khmermart.service;

import com.khmermart.model.enumerate.OrderStatus;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class OrderStateMachine {

  private static final Map<OrderStatus, Set<OrderStatus>> TRANSITIONS =
      new EnumMap<>(OrderStatus.class);

  static {
    TRANSITIONS.put(
        OrderStatus.PENDING_PAYMENT,
        Set.of(OrderStatus.CONFIRMED, OrderStatus.CANCELLED, OrderStatus.PAYMENT_EXPIRED));
    TRANSITIONS.put(
        OrderStatus.CONFIRMED,
        Set.of(
            OrderStatus.PROCESSING,
            OrderStatus.SHIPPED,
            OrderStatus.CANCELLED,
            OrderStatus.REFUNDED));
    TRANSITIONS.put(
        OrderStatus.PROCESSING,
        Set.of(OrderStatus.SHIPPED, OrderStatus.CANCELLED, OrderStatus.REFUNDED));
    TRANSITIONS.put(OrderStatus.SHIPPED, Set.of(OrderStatus.OUT_FOR_DELIVERY));
    TRANSITIONS.put(OrderStatus.OUT_FOR_DELIVERY, Set.of(OrderStatus.DELIVERED));
    TRANSITIONS.put(OrderStatus.DELIVERED, Set.of(OrderStatus.REFUNDED));
    TRANSITIONS.put(OrderStatus.CANCELLED, Collections.emptySet());
    TRANSITIONS.put(OrderStatus.PAYMENT_EXPIRED, Collections.emptySet());
    TRANSITIONS.put(OrderStatus.REFUNDED, Collections.emptySet());
  }

  public boolean canTransition(OrderStatus from, OrderStatus to) {
    if (from == null || to == null) {
      return false;
    }
    return TRANSITIONS.getOrDefault(from, Collections.emptySet()).contains(to);
  }
}
