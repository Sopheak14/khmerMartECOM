package com.khmermart.service.impl;

import com.khmermart.event.PaymentStatusChangedEvent;
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
import com.khmermart.model.response.PaymentCheckResultDto;
import com.khmermart.repository.CouponRedemptionRepository;
import com.khmermart.repository.InventoryMovementRepository;
import com.khmermart.repository.InventoryRepository;
import com.khmermart.repository.OrderItemRepository;
import com.khmermart.repository.OrderRepository;
import com.khmermart.repository.OrderStatusHistoryRepository;
import com.khmermart.repository.PaymentRepository;
import com.khmermart.service.KhqrPaymentChecker;
import com.khmermart.service.SettingService;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

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
  private final KhqrPaymentChecker paymentChecker;
  private final ApplicationEventPublisher eventPublisher;
  private final SettingService settingService;
  private final TransactionTemplate transactionTemplate;

  public PaymentExpiryJob(
      PaymentRepository paymentRepository,
      OrderRepository orderRepository,
      OrderItemRepository orderItemRepository,
      OrderStatusHistoryRepository orderStatusHistoryRepository,
      InventoryRepository inventoryRepository,
      InventoryMovementRepository movementRepository,
      CouponRedemptionRepository couponRedemptionRepository,
      KhqrPaymentChecker paymentChecker,
      ApplicationEventPublisher eventPublisher,
      SettingService settingService,
      TransactionTemplate transactionTemplate) {
    this.paymentRepository = paymentRepository;
    this.orderRepository = orderRepository;
    this.orderItemRepository = orderItemRepository;
    this.orderStatusHistoryRepository = orderStatusHistoryRepository;
    this.inventoryRepository = inventoryRepository;
    this.movementRepository = movementRepository;
    this.couponRedemptionRepository = couponRedemptionRepository;
    this.paymentChecker = paymentChecker;
    this.eventPublisher = eventPublisher;
    this.settingService = settingService;
    this.transactionTemplate = transactionTemplate;
  }

  @Scheduled(fixedDelay = 30000)
  public void runExpiryCheck() {
    expirePendingPayments();
  }

  /**
   * Not @Transactional on purpose: runExpiryCheck() calls this on the same bean, so a class-level
   * transaction would be bypassed by the proxy. Each payment gets its own transaction instead, so
   * one failure cannot roll back or block the others.
   */
  public void expirePendingPayments() {
    Instant now = Instant.now();
    List<Payment> expiredPayments =
        paymentRepository.findByStatusAndQrExpiresAtBefore(PaymentStatus.PENDING, now);

    for (Payment payment : expiredPayments) {
      try {
        transactionTemplate.executeWithoutResult(status -> expireSinglePayment(payment, now));
      } catch (Exception e) {
        log.error("Failed to expire payment {}: {}", payment.getId(), e.getMessage(), e);
      }
    }
  }

  private void expireSinglePayment(Payment payment, Instant now) {
    if ("LIVE".equalsIgnoreCase(settingService.getKhqrMode()) && payment.getKhqrMd5() != null) {
      try {
        PaymentCheckResultDto res = paymentChecker.check(payment.getId(), "EXPIRY_JOB");
        if (res.outcome() == PaymentCheckResultDto.Outcome.PAID
            || res.outcome() == PaymentCheckResultDto.Outcome.ALREADY_PAID) {
          log.info(
              "Payment {} was paid right at expiry boundary. Confirmed instead of expiring.",
              payment.getId());
          return;
        }
      } catch (Exception e) {
        log.warn(
            "Final check before expiring payment {} failed: {}", payment.getId(), e.getMessage());
      }
      // The check updated the row; reload so we do not overwrite it with a stale version.
      payment = paymentRepository.findById(payment.getId()).orElse(payment);
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
          inventoryRepository.releaseReserved(inv.getId(), item.getQuantity());

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
