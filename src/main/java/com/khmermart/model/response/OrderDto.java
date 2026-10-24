package com.khmermart.model.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.khmermart.model.enumerate.OrderStatus;
import com.khmermart.model.enumerate.PaymentMethod;
import com.khmermart.model.enumerate.PaymentStatus;
import java.time.Instant;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record OrderDto(
    Long id,
    String orderNumber,
    OrderStatus status,
    PaymentStatus paymentStatus,
    PaymentMethod paymentMethod,
    List<OrderItemDto> items,
    Long subtotalCents,
    Long discountCents,
    Long shippingCents,
    Long totalCents,
    String couponCode,
    AddressDto shippingAddress,
    ShippingMethodDto shippingMethod,
    String trackingNumber,
    String carrier,
    Instant paymentDeadline,
    Instant placedAt,
    Instant confirmedAt,
    Instant shippedAt,
    Instant deliveredAt,
    Instant cancelledAt,
    String cancelReason,
    List<OrderTimelineDto> timeline,
    boolean canCancel,
    boolean canReview,
    String invoiceUrl,
    AppUserDto customer) {

  public OrderDto(
      Long id,
      String orderNumber,
      OrderStatus status,
      PaymentStatus paymentStatus,
      PaymentMethod paymentMethod,
      List<OrderItemDto> items,
      Long subtotalCents,
      Long discountCents,
      Long shippingCents,
      Long totalCents,
      String couponCode,
      AddressDto shippingAddress,
      ShippingMethodDto shippingMethod,
      String trackingNumber,
      String carrier,
      Instant paymentDeadline,
      Instant placedAt,
      Instant confirmedAt,
      Instant shippedAt,
      Instant deliveredAt,
      Instant cancelledAt,
      String cancelReason,
      List<OrderTimelineDto> timeline,
      boolean canCancel,
      boolean canReview,
      String invoiceUrl) {
    this(
        id,
        orderNumber,
        status,
        paymentStatus,
        paymentMethod,
        items,
        subtotalCents,
        discountCents,
        shippingCents,
        totalCents,
        couponCode,
        shippingAddress,
        shippingMethod,
        trackingNumber,
        carrier,
        paymentDeadline,
        placedAt,
        confirmedAt,
        shippedAt,
        deliveredAt,
        cancelledAt,
        cancelReason,
        timeline,
        canCancel,
        canReview,
        invoiceUrl,
        null);
  }
}
