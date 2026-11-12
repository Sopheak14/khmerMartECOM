package com.khmermart.service;

import com.khmermart.common.exception.CustomException;
import com.khmermart.common.exception.ErrorCode;
import com.khmermart.event.CancelRequestRejectedEvent;
import com.khmermart.event.OrderCancelledEvent;
import com.khmermart.model.entity.AppUser;
import com.khmermart.model.entity.Inventory;
import com.khmermart.model.entity.InventoryMovement;
import com.khmermart.model.entity.Order;
import com.khmermart.model.entity.OrderItem;
import com.khmermart.model.entity.OrderStatusHistory;
import com.khmermart.model.entity.Payment;
import com.khmermart.model.entity.Refund;
import com.khmermart.model.enumerate.CouponRedemptionStatus;
import com.khmermart.model.enumerate.InventoryMovementType;
import com.khmermart.model.enumerate.OrderStatus;
import com.khmermart.model.enumerate.PaidMode;
import com.khmermart.model.enumerate.PaymentStatus;
import com.khmermart.repository.AppUserRepository;
import com.khmermart.repository.CouponRedemptionRepository;
import com.khmermart.repository.CouponRepository;
import com.khmermart.repository.InventoryMovementRepository;
import com.khmermart.repository.InventoryRepository;
import com.khmermart.repository.OrderItemRepository;
import com.khmermart.repository.OrderRepository;
import com.khmermart.repository.OrderStatusHistoryRepository;
import com.khmermart.repository.PaymentRepository;
import com.khmermart.repository.RefundRepository;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cancellation rules. Money is never "refunded" by software: a LIVE (KHQR) or COD refund is a
 * manual transfer, so it stays REFUND_PENDING until an admin records the bank reference.
 */
@Service
public class OrderCancellationService {

  private final OrderRepository orderRepository;
  private final OrderItemRepository orderItemRepository;
  private final OrderStatusHistoryRepository historyRepository;
  private final PaymentRepository paymentRepository;
  private final RefundRepository refundRepository;
  private final InventoryRepository inventoryRepository;
  private final InventoryMovementRepository movementRepository;
  private final CouponRepository couponRepository;
  private final CouponRedemptionRepository couponRedemptionRepository;
  private final AppUserRepository appUserRepository;
  private final AuditLogService auditLogService;
  private final ApplicationEventPublisher eventPublisher;

  public OrderCancellationService(
      OrderRepository orderRepository,
      OrderItemRepository orderItemRepository,
      OrderStatusHistoryRepository historyRepository,
      PaymentRepository paymentRepository,
      RefundRepository refundRepository,
      InventoryRepository inventoryRepository,
      InventoryMovementRepository movementRepository,
      CouponRepository couponRepository,
      CouponRedemptionRepository couponRedemptionRepository,
      AppUserRepository appUserRepository,
      AuditLogService auditLogService,
      ApplicationEventPublisher eventPublisher) {
    this.orderRepository = orderRepository;
    this.orderItemRepository = orderItemRepository;
    this.historyRepository = historyRepository;
    this.paymentRepository = paymentRepository;
    this.refundRepository = refundRepository;
    this.inventoryRepository = inventoryRepository;
    this.movementRepository = movementRepository;
    this.couponRepository = couponRepository;
    this.couponRedemptionRepository = couponRedemptionRepository;
    this.appUserRepository = appUserRepository;
    this.auditLogService = auditLogService;
    this.eventPublisher = eventPublisher;
  }

  // ------------------------------------------------------------------ customer

  /** Customer cancels an UNPAID order directly: stock released, no refund involved. */
  @Transactional
  public Order cancelByCustomer(Long userId, String orderNumber, String reason, String ip) {
    Order order = findOwned(userId, orderNumber);
    OrderStatus status = order.getStatus();

    if (status == OrderStatus.CANCELLED || status == OrderStatus.PAYMENT_EXPIRED) {
      throw new CustomException(
          ErrorCode.INVALID_STATUS_TRANSITION, "Order is already cancelled or expired: " + status);
    }
    if (isShippedOrLater(status)) {
      throw new CustomException(
          ErrorCode.CANCEL_NOT_ALLOWED,
          "Cannot cancel order that has already shipped or completed: " + status);
    }
    if (isPaid(order)) {
      throw new CustomException(
          ErrorCode.CANCEL_NOT_ALLOWED,
          "This order is already paid. Send a cancellation request instead:"
              + " POST /orders/"
              + orderNumber
              + "/cancel-request");
    }

    String text = reason != null && !reason.isBlank() ? reason : "Cancelled by customer";
    Map<String, Object> before = snapshot(order, null);
    cancelNow(order, text, userId, text);
    releaseStockAndCoupon(order, userId);
    order = orderRepository.save(order);
    auditLogService.log(
        userId, "CUSTOMER_CANCEL_ORDER", "ORDER", orderNumber, before, snapshot(order, null), ip);
    publishCancelled(order, text, false, null, null);
    return order;
  }

  /** Customer asks to cancel a PAID, not-yet-shipped order. An admin decides. */
  @Transactional
  public Order requestCancellation(Long userId, String orderNumber, String reason, String ip) {
    Order order = findOwned(userId, orderNumber);
    OrderStatus status = order.getStatus();

    if (status == OrderStatus.CANCELLED
        || status == OrderStatus.PAYMENT_EXPIRED
        || isShippedOrLater(status)) {
      throw new CustomException(
          ErrorCode.CANCEL_NOT_ALLOWED, "This order can no longer be cancelled: " + status);
    }
    if (!isPaid(order)) {
      throw new CustomException(
          ErrorCode.CANCEL_NOT_ALLOWED,
          "This order is not paid yet. Cancel it directly: POST /orders/"
              + orderNumber
              + "/cancel");
    }
    if (order.isCancelRequested()) {
      throw new CustomException(ErrorCode.CANCEL_ALREADY_REQUESTED);
    }

    Instant now = Instant.now();
    Map<String, Object> before = snapshot(order, null);
    order.setCancelRequested(true);
    order.setCancelRequestReason(reason);
    order.setCancelRequestedAt(now);
    order.setNeedsAttention(true);
    order.setAttentionNote(trim("Customer requested cancellation: " + reason, 500));
    order = orderRepository.save(order);

    history(
        order,
        status.name(),
        status.name(),
        "Cancellation requested by customer: " + reason,
        userId,
        now);
    auditLogService.log(
        userId, "CUSTOMER_CANCEL_REQUEST", "ORDER", orderNumber, before, snapshot(order, null), ip);
    return order;
  }

  // --------------------------------------------------------------------- admin

  /** Staff/admin cancels an order (approving a request or on their own). */
  @Transactional
  public Order adminCancel(String orderNumber, String reason, Long actorId, String ip) {
    Order order =
        orderRepository
            .findByOrderNumber(orderNumber)
            .orElseThrow(() -> new CustomException(ErrorCode.NOT_FOUND, "Order not found"));
    OrderStatus status = order.getStatus();
    if (status != OrderStatus.PENDING_PAYMENT
        && status != OrderStatus.CONFIRMED
        && status != OrderStatus.PROCESSING) {
      throw new CustomException(
          ErrorCode.CANCEL_NOT_ALLOWED, "Order in status " + status + " cannot be cancelled");
    }

    String text = reason != null && !reason.isBlank() ? reason : "Cancelled by admin";
    Payment payment =
        isPaid(order)
            ? paymentRepository.findFirstByOrderIdOrderByIdDesc(order.getId()).orElse(null)
            : null;
    Map<String, Object> before = snapshot(order, payment);
    Instant now = Instant.now();

    // stock + coupon first: RETURN when paid, RELEASE when unpaid (reads paymentStatus)
    releaseStockAndCoupon(order, actorId);
    cancelNow(order, text, actorId, "Cancelled by admin: " + text);

    boolean refundPending = false;
    Long refundCents = null;
    String payer = null;
    if (payment != null) {
      refundCents = payment.getAmountCents();
      PaidMode mode = RefundRules.effectiveMode(payment);
      if (mode == PaidMode.MOCK) {
        payment.setStatus(PaymentStatus.REFUNDED);
        order.setPaymentStatus(PaymentStatus.REFUNDED);
        saveRefund(
            payment, text, actorId, now, null, "COMPLETED", "MOCK payment — no real money moved");
        history(
            order, "CANCELLED", "CANCELLED", "Refunded (MOCK — no real money moved)", actorId, now);
      } else {
        refundPending = true;
        payer = payment.getFromAccountId();
        payment.setStatus(PaymentStatus.REFUND_PENDING);
        order.setPaymentStatus(PaymentStatus.REFUND_PENDING);
        saveRefund(payment, text, actorId, now, payer, "PENDING", null);
        history(
            order,
            "CANCELLED",
            "CANCELLED",
            "Refund pending: "
                + RefundRules.usd(refundCents)
                + (payer != null ? " to " + payer : "")
                + (mode == PaidMode.COD
                    ? " (cash to be returned by hand)"
                    : " (manual bank transfer)"),
            actorId,
            now);
      }
      paymentRepository.save(payment);
    }

    order = orderRepository.save(order);
    auditLogService.log(
        actorId, "ADMIN_CANCEL_ORDER", "ORDER", orderNumber, before, snapshot(order, payment), ip);
    publishCancelled(order, text, refundPending, refundCents, payer);
    return order;
  }

  /** Staff/admin refuses a customer's cancellation request; the order carries on. */
  @Transactional
  public Order rejectCancelRequest(String orderNumber, String reason, Long actorId, String ip) {
    Order order =
        orderRepository
            .findByOrderNumber(orderNumber)
            .orElseThrow(() -> new CustomException(ErrorCode.NOT_FOUND, "Order not found"));
    if (!order.isCancelRequested()) {
      throw new CustomException(ErrorCode.CANCEL_NOT_REQUESTED);
    }
    Map<String, Object> before = snapshot(order, null);
    Instant now = Instant.now();
    order.setCancelRequested(false);
    order.setNeedsAttention(false);
    order.setAttentionNote(null);
    order = orderRepository.save(order);

    history(
        order,
        order.getStatus().name(),
        order.getStatus().name(),
        "Cancellation rejected by admin: " + reason,
        actorId,
        now);
    auditLogService.log(
        actorId,
        "ADMIN_REJECT_CANCEL_REQUEST",
        "ORDER",
        orderNumber,
        before,
        snapshot(order, null),
        ip);

    AppUser user = appUserRepository.findById(order.getUserId()).orElse(null);
    eventPublisher.publishEvent(
        new CancelRequestRejectedEvent(
            order.getOrderNumber(),
            order.getUserId(),
            user != null ? user.getEmail() : null,
            user != null ? user.getFullName() : null,
            reason));
    return order;
  }

  // ------------------------------------------------------------------- helpers

  private Order findOwned(Long userId, String orderNumber) {
    Order order =
        orderRepository
            .findByOrderNumber(orderNumber)
            .orElseThrow(() -> new CustomException(ErrorCode.NOT_FOUND, "Order not found"));
    if (userId != null && !order.getUserId().equals(userId)) {
      throw new CustomException(ErrorCode.NOT_FOUND, "Order not found");
    }
    return order;
  }

  private static boolean isShippedOrLater(OrderStatus s) {
    return s == OrderStatus.SHIPPED
        || s == OrderStatus.OUT_FOR_DELIVERY
        || s == OrderStatus.DELIVERED
        || s == OrderStatus.REFUNDED;
  }

  private static boolean isPaid(Order order) {
    return order.getPaymentStatus() == PaymentStatus.PAID;
  }

  private void cancelNow(Order order, String reason, Long actorId, String timelineNote) {
    Instant now = Instant.now();
    OrderStatus from = order.getStatus();
    order.setStatus(OrderStatus.CANCELLED);
    order.setCancelledAt(now);
    order.setCancelReason(trim(reason, 255));
    order.setCancelRequested(false);
    order.setNeedsAttention(false);
    order.setAttentionNote(null);
    history(order, from.name(), OrderStatus.CANCELLED.name(), timelineNote, actorId, now);
  }

  private void history(Order order, String from, String to, String note, Long actorId, Instant at) {
    historyRepository.save(
        OrderStatusHistory.builder()
            .orderId(order.getId())
            .fromStatus(from)
            .toStatus(to)
            .note(note)
            .actorId(actorId)
            .createdAt(at)
            .build());
  }

  private Refund saveRefund(
      Payment payment,
      String reason,
      Long actorId,
      Instant now,
      String payer,
      String status,
      String note) {
    Refund refund =
        Refund.builder()
            .paymentId(payment.getId())
            .amountCents(payment.getAmountCents())
            .reason(trim(reason, 255))
            .status(status)
            .actorId(actorId)
            .payerAccount(payer)
            .requestedAt(now)
            .approvedBy(actorId)
            .note(note)
            .createdAt(now)
            .build();
    if ("COMPLETED".equals(status)) {
      refund.setCompletedAt(now);
      refund.setCompletedBy(actorId);
    }
    return refundRepository.save(refund);
  }

  /** Paid → stock goes back on hand (RETURN); unpaid → reservation dropped (RELEASE). */
  private void releaseStockAndCoupon(Order order, Long actorId) {
    Instant now = Instant.now();
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
      if (inv == null) {
        continue;
      }
      InventoryMovementType type;
      if (isPaid(order)) {
        inventoryRepository.addOnHand(inv.getId(), item.getQuantity());
        type = InventoryMovementType.RETURN;
      } else {
        inventoryRepository.releaseReserved(inv.getId(), item.getQuantity());
        type = InventoryMovementType.RELEASE;
      }
      movementRepository.save(
          InventoryMovement.builder()
              .inventoryId(inv.getId())
              .type(type)
              .quantity(item.getQuantity())
              .reason("Order cancelled: " + order.getOrderNumber())
              .actorId(actorId)
              .refOrderId(order.getId())
              .createdAt(now)
              .build());
    }

    if (order.getCouponCode() != null) {
      couponRedemptionRepository
          .findByOrderId(order.getId())
          .ifPresent(
              r -> {
                r.setStatus(CouponRedemptionStatus.RELEASED);
                couponRedemptionRepository.save(r);
              });
      couponRepository
          .findByCode(order.getCouponCode())
          .ifPresent(
              c -> {
                c.setUsedCount(Math.max(0, c.getUsedCount() - 1));
                couponRepository.save(c);
              });
    }
  }

  private void publishCancelled(
      Order order, String reason, boolean refundPending, Long refundCents, String payer) {
    AppUser user = appUserRepository.findById(order.getUserId()).orElse(null);
    eventPublisher.publishEvent(
        new OrderCancelledEvent(
            order.getId(),
            order.getOrderNumber(),
            order.getUserId(),
            user != null ? user.getEmail() : null,
            user != null ? user.getFullName() : null,
            user != null ? user.getPhone() : null,
            reason,
            refundPending,
            refundCents,
            payer));
  }

  private static Map<String, Object> snapshot(Order o, Payment p) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("orderStatus", o.getStatus());
    m.put("orderPaymentStatus", o.getPaymentStatus());
    m.put("cancelRequested", o.isCancelRequested());
    if (p != null) {
      m.put("paymentId", p.getId());
      m.put("paymentStatus", p.getStatus());
      m.put("paidMode", RefundRules.effectiveMode(p));
    }
    return m;
  }

  private static String trim(String s, int max) {
    return s != null && s.length() > max ? s.substring(0, max) : s;
  }
}
