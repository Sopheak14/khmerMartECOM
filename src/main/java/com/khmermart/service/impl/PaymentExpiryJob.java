package com.khmermart.service.impl;

import com.khmermart.common.event.PaymentStatusChangedEvent;
import com.khmermart.config.AppProperty;
import com.khmermart.model.entity.Inventory;
import com.khmermart.model.entity.InventoryMovement;
import com.khmermart.model.entity.Order;
import com.khmermart.model.entity.OrderItem;
import com.khmermart.model.entity.OrderStatusHistory;
import com.khmermart.model.entity.Payment;
import com.khmermart.model.enumerate.CouponRedemptionStatus;
import com.khmermart.model.enumerate.InventoryMovementType;
import com.khmermart.model.enumerate.OrderStatus;
import com.khmermart.model.enumerate.PaymentStatus;
import com.khmermart.repository.CouponRedemptionRepository;
import com.khmermart.repository.InventoryMovementRepository;
import com.khmermart.repository.InventoryRepository;
import com.khmermart.repository.OrderItemRepository;
import com.khmermart.repository.OrderRepository;
import com.khmermart.repository.OrderStatusHistoryRepository;
import com.khmermart.repository.PaymentRepository;
import com.khmermart.service.BakongApiClient;
import com.khmermart.service.PaymentConfirmationService;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class PaymentExpiryJob {

  private static final Logger log = LoggerFactory.getLogger(PaymentExpiryJob.class);

  private final PaymentRepository paymentRepository;
  private final OrderRepository orderRepository;
  private final OrderItemRepository orderItemRepository;
  private final OrderStatusHistoryRepository orderStatusHistoryRepository;
  private final InventoryRepository inventoryRepository;
  private final InventoryMovementRepository movementRepository;
  private final CouponRedemptionRepository couponRedemptionRepository;
  private final BakongApiClient bakongApiClient;
  private final PaymentConfirmationService paymentConfirmationService;
  private final ApplicationEventPublisher eventPublisher;
  private final AppProperty appProperty;

  public PaymentExpiryJob(
      PaymentRepository paymentRepository,
      OrderRepository orderRepository,
      OrderItemRepository orderItemRepository,
      OrderStatusHistoryRepository orderStatusHistoryRepository,
      InventoryRepository inventoryRepository,
      InventoryMovementRepository movementRepository,
      CouponRedemptionRepository couponRedemptionRepository,
      BakongApiClient bakongApiClient,
      PaymentConfirmationService paymentConfirmationService,
      ApplicationEventPublisher eventPublisher,
      AppProperty appProperty) {
    this.paymentRepository = paymentRepository;
    this.orderRepository = orderRepository;
    this.orderItemRepository = orderItemRepository;
    this.orderStatusHistoryRepository = orderStatusHistoryRepository;
    this.inventoryRepository = inventoryRepository;
    this.movementRepository = movementRepository;
    this.couponRedemptionRepository = couponRedemptionRepository;
    this.bakongApiClient = bakongApiClient;
    this.paymentConfirmationService = paymentConfirmationService;
    this.eventPublisher = eventPublisher;
    this.appProperty = appProperty;
  }

  @Scheduled(fixedDelay = 30000)
  public void runExpiryCheck() {
    expirePendingPayments();
  }

  @Transactional
  public void expirePendingPayments() {
    Instant now = Instant.now();
    List<Payment> expiredPayments =
        paymentRepository.findByStatusAndQrExpiresAtBefore(PaymentStatus.PENDING, now);

    for (Payment payment : expiredPayments) {
      try {
        expireSinglePayment(payment, now);
      } catch (Exception e) {
        log.error("Failed to expire payment {}: {}", payment.getId(), e.getMessage(), e);
      }
    }
  }

  private void expireSinglePayment(Payment payment, Instant now) {
    if ("LIVE".equalsIgnoreCase(appProperty.khqr().mode()) && payment.getKhqrMd5() != null) {
      try {
        var resp = bakongApiClient.checkTransactionByMd5(payment.getKhqrMd5());
        if (resp != null
            && resp.responseCode() != null
            && resp.responseCode() == 0
            && resp.data() != null) {
          log.info(
              "Payment {} was paid right at expiry boundary. Confirming instead of expiring.",
              payment.getId());
          paymentConfirmationService.confirm(payment.getId(), resp.data());
          return;
        }
      } catch (Exception ignored) {
      }
    }

    payment.setStatus(PaymentStatus.EXPIRED);
    paymentRepository.save(payment);

    Order order = orderRepository.findById(payment.getOrderId()).orElse(null);
    if (order == null) {
      return;
    }

    boolean hasActivePending =
        paymentRepository.findByOrderIdOrderByIdDesc(order.getId()).stream()
            .anyMatch(
                p ->
                    p.getStatus() == PaymentStatus.PENDING
                        && p.getQrExpiresAt() != null
                        && p.getQrExpiresAt().isAfter(now));

    if (!hasActivePending && order.getStatus() == OrderStatus.PENDING_PAYMENT) {
      OrderStatus fromStatus = order.getStatus();
      order.setStatus(OrderStatus.PAYMENT_EXPIRED);
      order.setPaymentStatus(PaymentStatus.EXPIRED);
      orderRepository.save(order);

      OrderStatusHistory history =
          OrderStatusHistory.builder()
              .orderId(order.getId())
              .fromStatus(fromStatus.name())
              .toStatus(OrderStatus.PAYMENT_EXPIRED.name())
              .note("Payment expired")
              .actorId(order.getUserId())
              .createdAt(now)
              .build();
      orderStatusHistoryRepository.save(history);

      List<OrderItem> items = orderItemRepository.findByOrderId(order.getId());
      for (OrderItem item : items) {
        Inventory inv =
            item.getVariantId() != null
                ? inventoryRepository
                    .findByProductIdAndVariantId(item.getProductId(), item.getVariantId())
                    .orElse(null)
                : inventoryRepository
                    .findByProductIdAndVariantIdIsNull(item.getProductId())
                    .orElse(null);

        if (inv != null) {
          inv.setQuantityReserved(Math.max(0, inv.getQuantityReserved() - item.getQuantity()));
          inventoryRepository.save(inv);

          InventoryMovement movement =
              InventoryMovement.builder()
                  .inventoryId(inv.getId())
                  .type(InventoryMovementType.RELEASE)
                  .quantity(item.getQuantity())
                  .reason("Payment expired for order: " + order.getOrderNumber())
                  .actorId(order.getUserId())
                  .refOrderId(order.getId())
                  .createdAt(now)
                  .build();
          movementRepository.save(movement);
        }
      }

      if (order.getCouponCode() != null) {
        couponRedemptionRepository
            .findByOrderId(order.getId())
            .ifPresent(
                r -> {
                  r.setStatus(CouponRedemptionStatus.RELEASED);
                  couponRedemptionRepository.save(r);
                });
      }

      eventPublisher.publishEvent(
          new PaymentStatusChangedEvent(
              payment.getId(),
              order.getId(),
              PaymentStatus.EXPIRED,
              OrderStatus.PAYMENT_EXPIRED,
              null,
              null,
              payment.getQrExpiresAt()));
    }
  }
}
