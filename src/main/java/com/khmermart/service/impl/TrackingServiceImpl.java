package com.khmermart.service.impl;

import com.khmermart.common.exception.CustomException;
import com.khmermart.common.exception.ErrorCode;
import com.khmermart.event.OrderDeliveredEvent;
import com.khmermart.event.OrderOutForDeliveryEvent;
import com.khmermart.model.entity.AppUser;
import com.khmermart.model.entity.Order;
import com.khmermart.model.entity.OrderStatusHistory;
import com.khmermart.model.entity.Payment;
import com.khmermart.model.entity.Shipment;
import com.khmermart.model.entity.TrackingEvent;
import com.khmermart.model.enumerate.OrderStatus;
import com.khmermart.model.enumerate.PaymentMethod;
import com.khmermart.model.enumerate.PaymentStatus;
import com.khmermart.model.enumerate.ShipmentStatus;
import com.khmermart.model.request.AddTrackingEventRequest;
import com.khmermart.model.response.TrackingDto;
import com.khmermart.model.response.TrackingEventDto;
import com.khmermart.repository.AppUserRepository;
import com.khmermart.repository.OrderRepository;
import com.khmermart.repository.OrderStatusHistoryRepository;
import com.khmermart.repository.PaymentRepository;
import com.khmermart.repository.ShipmentRepository;
import com.khmermart.repository.TrackingEventRepository;
import com.khmermart.service.TrackingService;
import java.time.Instant;
import java.util.List;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TrackingServiceImpl implements TrackingService {

  private final ShipmentRepository shipmentRepository;
  private final TrackingEventRepository trackingEventRepository;
  private final OrderRepository orderRepository;
  private final OrderStatusHistoryRepository orderStatusHistoryRepository;
  private final PaymentRepository paymentRepository;
  private final AppUserRepository appUserRepository;
  private final ApplicationEventPublisher eventPublisher;

  public TrackingServiceImpl(
      ShipmentRepository shipmentRepository,
      TrackingEventRepository trackingEventRepository,
      OrderRepository orderRepository,
      OrderStatusHistoryRepository orderStatusHistoryRepository,
      PaymentRepository paymentRepository,
      AppUserRepository appUserRepository,
      ApplicationEventPublisher eventPublisher) {
    this.shipmentRepository = shipmentRepository;
    this.trackingEventRepository = trackingEventRepository;
    this.orderRepository = orderRepository;
    this.orderStatusHistoryRepository = orderStatusHistoryRepository;
    this.paymentRepository = paymentRepository;
    this.appUserRepository = appUserRepository;
    this.eventPublisher = eventPublisher;
  }

  @Override
  @Transactional(readOnly = true)
  public TrackingDto getTracking(String trackingNumber) {
    Shipment shipment =
        shipmentRepository
            .findByTrackingNumber(trackingNumber)
            .orElseThrow(
                () -> new CustomException(ErrorCode.NOT_FOUND, "Shipment tracking not found"));

    Order order =
        orderRepository
            .findById(shipment.getOrderId())
            .orElseThrow(() -> new CustomException(ErrorCode.NOT_FOUND, "Order not found"));

    List<TrackingEvent> events =
        trackingEventRepository.findByShipmentIdOrderByCreatedAtAsc(shipment.getId());
    List<TrackingEventDto> eventDtos =
        events.stream()
            .map(
                e ->
                    new TrackingEventDto(
                        e.getStatus(), e.getLocation(), e.getNote(), e.getCreatedAt()))
            .toList();

    return new TrackingDto(
        shipment.getTrackingNumber(),
        shipment.getCarrier(),
        order.getOrderNumber(),
        shipment.getStatus(),
        eventDtos,
        shipment.getEstimatedDelivery());
  }

  @Override
  @Transactional
  public TrackingEventDto addTrackingEvent(String orderNumber, AddTrackingEventRequest request) {
    Order order =
        orderRepository
            .findByOrderNumber(orderNumber)
            .orElseThrow(() -> new CustomException(ErrorCode.NOT_FOUND, "Order not found"));

    Shipment shipment =
        shipmentRepository
            .findByOrderId(order.getId())
            .orElseThrow(
                () ->
                    new CustomException(
                        ErrorCode.NOT_FOUND, "Shipment not found for order " + orderNumber));

    validateShipmentTransition(shipment.getStatus(), request.status());

    Instant now = Instant.now();
    TrackingEvent event =
        TrackingEvent.builder()
            .shipmentId(shipment.getId())
            .status(request.status())
            .location(request.location())
            .note(request.note())
            .createdAt(now)
            .build();
    event = trackingEventRepository.save(event);

    shipment.setStatus(request.status());
    shipmentRepository.save(shipment);

    AppUser user = appUserRepository.findById(order.getUserId()).orElse(null);
    String email = user != null ? user.getEmail() : null;
    String fullName = user != null ? user.getFullName() : null;
    String phone = user != null ? user.getPhone() : null;

    if (request.status() == ShipmentStatus.OUT_FOR_DELIVERY) {
      OrderStatus fromStatus = order.getStatus();
      order.setStatus(OrderStatus.OUT_FOR_DELIVERY);
      orderRepository.save(order);

      OrderStatusHistory history =
          OrderStatusHistory.builder()
              .orderId(order.getId())
              .fromStatus(fromStatus.name())
              .toStatus(OrderStatus.OUT_FOR_DELIVERY.name())
              .note("Out for delivery: " + (request.note() != null ? request.note() : ""))
              .createdAt(now)
              .build();
      orderStatusHistoryRepository.save(history);

      eventPublisher.publishEvent(
          new OrderOutForDeliveryEvent(
              order.getId(),
              order.getOrderNumber(),
              order.getUserId(),
              email,
              fullName,
              phone,
              shipment.getCarrier(),
              shipment.getTrackingNumber()));
    } else if (request.status() == ShipmentStatus.DELIVERED) {
      OrderStatus fromStatus = order.getStatus();
      order.setStatus(OrderStatus.DELIVERED);
      order.setDeliveredAt(now);

      // COD payment auto-mark rule:
      if (order.getPaymentMethod() == PaymentMethod.COD) {
        order.setPaymentStatus(PaymentStatus.PAID);
        Payment payment =
            paymentRepository.findFirstByOrderIdOrderByIdDesc(order.getId()).orElse(null);
        if (payment != null) {
          payment.setStatus(PaymentStatus.PAID);
          payment.setPaidAt(now);
          paymentRepository.save(payment);
        }
      }

      orderRepository.save(order);

      OrderStatusHistory history =
          OrderStatusHistory.builder()
              .orderId(order.getId())
              .fromStatus(fromStatus.name())
              .toStatus(OrderStatus.DELIVERED.name())
              .note("Delivered: " + (request.note() != null ? request.note() : ""))
              .createdAt(now)
              .build();
      orderStatusHistoryRepository.save(history);

      eventPublisher.publishEvent(
          new OrderDeliveredEvent(
              order.getId(), order.getOrderNumber(), order.getUserId(), email, fullName, phone));
    }

    return new TrackingEventDto(
        event.getStatus(), event.getLocation(), event.getNote(), event.getCreatedAt());
  }

  private void validateShipmentTransition(ShipmentStatus current, ShipmentStatus next) {
    if (current == next) {
      return;
    }

    boolean valid =
        switch (current) {
          case PREPARING -> next == ShipmentStatus.DISPATCHED;
          case DISPATCHED -> next == ShipmentStatus.IN_TRANSIT;
          case IN_TRANSIT ->
              next == ShipmentStatus.OUT_FOR_DELIVERY || next == ShipmentStatus.FAILED;
          case OUT_FOR_DELIVERY ->
              next == ShipmentStatus.DELIVERED || next == ShipmentStatus.FAILED;
          case DELIVERED, FAILED -> false;
        };

    if (!valid) {
      throw new CustomException(
          ErrorCode.INVALID_STATUS_TRANSITION,
          "Cannot transition shipment from " + current + " to " + next);
    }
  }
}
