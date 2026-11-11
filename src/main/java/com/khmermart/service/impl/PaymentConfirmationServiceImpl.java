package com.khmermart.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.khmermart.common.exception.CustomException;
import com.khmermart.common.exception.ErrorCode;
import com.khmermart.event.PaymentPaidEvent;
import com.khmermart.event.PaymentStatusChangedEvent;
import com.khmermart.model.entity.AppUser;
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
import com.khmermart.model.response.BakongTxData;
import com.khmermart.repository.AppUserRepository;
import com.khmermart.repository.CouponRedemptionRepository;
import com.khmermart.repository.CouponRepository;
import com.khmermart.repository.InventoryMovementRepository;
import com.khmermart.repository.InventoryRepository;
import com.khmermart.repository.OrderItemRepository;
import com.khmermart.repository.OrderRepository;
import com.khmermart.repository.OrderStatusHistoryRepository;
import com.khmermart.repository.PaymentRepository;
import com.khmermart.repository.ProductRepository;
import com.khmermart.service.InvoiceService;
import com.khmermart.service.OrderStateMachine;
import com.khmermart.service.PaymentConfirmationService;
import com.khmermart.service.RefundRules;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentConfirmationServiceImpl implements PaymentConfirmationService {

  private static final Logger log = LoggerFactory.getLogger(PaymentConfirmationServiceImpl.class);

  private final PaymentRepository paymentRepository;
  private final OrderRepository orderRepository;
  private final OrderItemRepository orderItemRepository;
  private final OrderStatusHistoryRepository orderStatusHistoryRepository;
  private final InventoryRepository inventoryRepository;
  private final InventoryMovementRepository movementRepository;
  private final ProductRepository productRepository;
  private final CouponRepository couponRepository;
  private final CouponRedemptionRepository couponRedemptionRepository;
  private final AppUserRepository appUserRepository;
  private final InvoiceService invoiceService;
  private final OrderStateMachine orderStateMachine;
  private final ApplicationEventPublisher eventPublisher;
  private final ObjectMapper objectMapper;

  public PaymentConfirmationServiceImpl(
      PaymentRepository paymentRepository,
      OrderRepository orderRepository,
      OrderItemRepository orderItemRepository,
      OrderStatusHistoryRepository orderStatusHistoryRepository,
      InventoryRepository inventoryRepository,
      InventoryMovementRepository movementRepository,
      ProductRepository productRepository,
      CouponRepository couponRepository,
      CouponRedemptionRepository couponRedemptionRepository,
      AppUserRepository appUserRepository,
      InvoiceService invoiceService,
      OrderStateMachine orderStateMachine,
      ApplicationEventPublisher eventPublisher,
      ObjectMapper objectMapper) {
    this.paymentRepository = paymentRepository;
    this.orderRepository = orderRepository;
    this.orderItemRepository = orderItemRepository;
    this.orderStatusHistoryRepository = orderStatusHistoryRepository;
    this.inventoryRepository = inventoryRepository;
    this.movementRepository = movementRepository;
    this.productRepository = productRepository;
    this.couponRepository = couponRepository;
    this.couponRedemptionRepository = couponRedemptionRepository;
    this.appUserRepository = appUserRepository;
    this.invoiceService = invoiceService;
    this.orderStateMachine = orderStateMachine;
    this.eventPublisher = eventPublisher;
    this.objectMapper = objectMapper;
  }

  @Override
  @Transactional
  public Payment confirm(Long paymentId, BakongTxData txData) {
    String rawJson = null;
    if (txData != null) {
      try {
        rawJson = objectMapper.writeValueAsString(txData);
      } catch (Exception ignored) {
      }
    }
    Payment confirmed =
        confirm(
            paymentId,
            txData != null ? txData.hash() : null,
            txData != null ? txData.fromAccountId() : null,
            txData != null ? txData.toAccountId() : null,
            rawJson);
    if (txData != null
        && txData.externalRef() != null
        && confirmed.getStatus() == PaymentStatus.PAID
        && confirmed.getExternalRef() == null) {
      confirmed.setExternalRef(txData.externalRef());
      confirmed = paymentRepository.save(confirmed);
    }
    return confirmed;
  }

  @Override
  @Transactional
  public Payment confirmLate(Long paymentId, BakongTxData txData) {
    Payment payment =
        paymentRepository
            .findById(paymentId)
            .orElseThrow(() -> new CustomException(ErrorCode.NOT_FOUND, "Payment not found"));
    if (payment.getStatus() == PaymentStatus.PAID) {
      return payment;
    }
    Order order =
        orderRepository
            .findById(payment.getOrderId())
            .orElseThrow(() -> new CustomException(ErrorCode.NOT_FOUND, "Order not found"));

    String rawJson = null;
    try {
      rawJson = objectMapper.writeValueAsString(txData);
    } catch (Exception ignored) {
    }
    Instant now = Instant.now();
    PaymentStatus previous = payment.getStatus();
    payment.setStatus(PaymentStatus.PAID);
    payment.setPaidMode(RefundRules.modeForHash(txData.hash()));
    payment.setBakongHash(txData.hash());
    payment.setFromAccountId(txData.fromAccountId());
    payment.setToAccountId(txData.toAccountId());
    payment.setExternalRef(txData.externalRef());
    payment.setRawResponse(rawJson);
    payment.setFailureReason(null);
    payment.setPaidAt(now);
    payment = paymentRepository.save(payment);

    String note =
        "LATE PAYMENT: Bakong confirmed "
            + txData.amount()
            + " "
            + txData.currency()
            + " (hash "
            + txData.hash()
            + ") after the payment was "
            + previous
            + ". Stock was released - review and fulfil or refund.";
    order.setPaymentStatus(PaymentStatus.PAID);
    order.setNeedsAttention(true);
    order.setAttentionNote(note.length() > 500 ? note.substring(0, 500) : note);
    orderRepository.save(order);

    orderStatusHistoryRepository.save(
        OrderStatusHistory.builder()
            .orderId(order.getId())
            .fromStatus(order.getStatus().name())
            .toStatus(order.getStatus().name())
            .note(note.length() > 500 ? note.substring(0, 500) : note)
            .actorId(order.getUserId())
            .createdAt(now)
            .build());

    log.warn(
        "Late payment recorded: paymentId={} order={} hash={} previousStatus={}",
        payment.getId(),
        order.getOrderNumber(),
        txData.hash(),
        previous);

    eventPublisher.publishEvent(
        new PaymentStatusChangedEvent(
            payment.getId(),
            order.getId(),
            PaymentStatus.PAID,
            order.getStatus(),
            now,
            txData.hash(),
            payment.getQrExpiresAt()));
    return payment;
  }

  @Override
  @Transactional
  public Payment confirm(
      Long paymentId, String hash, String fromAccountId, String toAccountId, String rawResponse) {
    Payment payment =
        paymentRepository
            .findById(paymentId)
            .orElseThrow(() -> new CustomException(ErrorCode.NOT_FOUND, "Payment not found"));

    // Idempotent: if already PAID, return current state
    if (payment.getStatus() == PaymentStatus.PAID) {
      log.info("Payment {} is already PAID. Skipping duplicate confirmation.", paymentId);
      return payment;
    }

    if (payment.getStatus() != PaymentStatus.PENDING) {
      log.warn(
          "Payment {} cannot be confirmed because status is {}", paymentId, payment.getStatus());
      return payment;
    }

    Order order =
        orderRepository
            .findById(payment.getOrderId())
            .orElseThrow(() -> new CustomException(ErrorCode.NOT_FOUND, "Order not found"));

    Instant now = Instant.now();

    // 1. Update Payment
    payment.setStatus(PaymentStatus.PAID);
    payment.setPaidMode(RefundRules.modeForHash(hash));
    payment.setBakongHash(hash);
    payment.setFromAccountId(fromAccountId);
    payment.setToAccountId(toAccountId);
    payment.setRawResponse(rawResponse);
    payment.setPaidAt(now);
    payment = paymentRepository.save(payment);

    // 2. Transition Order to CONFIRMED
    OrderStatus fromStatus = order.getStatus();
    if (orderStateMachine.canTransition(fromStatus, OrderStatus.CONFIRMED)) {
      order.setStatus(OrderStatus.CONFIRMED);
      order.setPaymentStatus(PaymentStatus.PAID);
      order.setConfirmedAt(now);
      orderRepository.save(order);

      OrderStatusHistory history =
          OrderStatusHistory.builder()
              .orderId(order.getId())
              .fromStatus(fromStatus.name())
              .toStatus(OrderStatus.CONFIRMED.name())
              .note("Payment confirmed (" + hash + ")")
              .actorId(order.getUserId())
              .createdAt(now)
              .build();
      orderStatusHistoryRepository.save(history);
    } else {
      order.setPaymentStatus(PaymentStatus.PAID);
      orderRepository.save(order);
    }

    // 3. Convert Inventory RESERVE -> SALE
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
        inventoryRepository.deductReservedSale(inv.getId(), item.getQuantity());

        InventoryMovement movement =
            InventoryMovement.builder()
                .inventoryId(inv.getId())
                .type(InventoryMovementType.SALE)
                .quantity(item.getQuantity())
                .reason("Order payment confirmed: " + order.getOrderNumber())
                .actorId(order.getUserId())
                .refOrderId(order.getId())
                .createdAt(now)
                .build();
        movementRepository.save(movement);
      }

      productRepository
          .findById(item.getProductId())
          .ifPresent(
              p -> {
                p.setSoldCount(p.getSoldCount() + item.getQuantity());
                productRepository.save(p);
              });
    }

    // 4. Update CouponRedemption -> REDEEMED and increment coupon.usedCount
    if (order.getCouponCode() != null) {
      couponRedemptionRepository
          .findByOrderId(order.getId())
          .ifPresent(
              redemption -> {
                redemption.setStatus(CouponRedemptionStatus.REDEEMED);
                couponRedemptionRepository.save(redemption);
              });

      couponRepository
          .findByCode(order.getCouponCode())
          .ifPresent(
              coupon -> {
                coupon.setUsedCount(coupon.getUsedCount() + 1);
                couponRepository.save(coupon);
              });
    }

    // 5. Publish events
    AppUser user = appUserRepository.findById(order.getUserId()).orElse(null);
    byte[] invoicePdf = null;
    try {
      invoicePdf = invoiceService.generateInvoicePdf(order.getOrderNumber());
    } catch (Exception e) {
      log.warn(
          "Failed to generate invoice PDF for order {}: {}",
          order.getOrderNumber(),
          e.getMessage());
    }

    eventPublisher.publishEvent(
        new PaymentPaidEvent(
            payment.getId(),
            order.getId(),
            order.getOrderNumber(),
            order.getUserId(),
            user != null ? user.getEmail() : null,
            user != null ? user.getFullName() : null,
            user != null ? user.getPhone() : null,
            payment.getAmountCents(),
            hash,
            invoicePdf));
    eventPublisher.publishEvent(
        new PaymentStatusChangedEvent(
            payment.getId(),
            order.getId(),
            PaymentStatus.PAID,
            order.getStatus(),
            now,
            hash,
            payment.getQrExpiresAt()));

    return payment;
  }
}
