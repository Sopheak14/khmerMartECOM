package com.khmermart.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.khmermart.common.exception.CustomException;
import com.khmermart.common.exception.ErrorCode;
import com.khmermart.model.entity.Inventory;
import com.khmermart.model.entity.InventoryMovement;
import com.khmermart.model.entity.Order;
import com.khmermart.model.entity.OrderItem;
import com.khmermart.model.entity.Payment;
import com.khmermart.model.entity.Refund;
import com.khmermart.model.enumerate.InventoryMovementType;
import com.khmermart.model.enumerate.OrderStatus;
import com.khmermart.model.enumerate.PaymentMethod;
import com.khmermart.model.enumerate.PaymentStatus;
import com.khmermart.model.request.RefundRequest;
import com.khmermart.model.response.AdminPaymentDto;
import com.khmermart.model.response.ReconciliationReportDto;
import com.khmermart.model.response.ReconciliationReportDto.ReconciliationMismatchDto;
import com.khmermart.repository.InventoryMovementRepository;
import com.khmermart.repository.InventoryRepository;
import com.khmermart.repository.OrderItemRepository;
import com.khmermart.repository.OrderRepository;
import com.khmermart.repository.PaymentRepository;
import com.khmermart.repository.RefundRepository;
import com.khmermart.service.AdminPaymentService;
import com.khmermart.service.AuditLogService;
import java.io.ByteArrayOutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminPaymentServiceImpl implements AdminPaymentService {

  private final PaymentRepository paymentRepository;
  private final OrderRepository orderRepository;
  private final OrderItemRepository orderItemRepository;
  private final InventoryRepository inventoryRepository;
  private final InventoryMovementRepository inventoryMovementRepository;
  private final RefundRepository refundRepository;
  private final AuditLogService auditLogService;
  private final ObjectMapper objectMapper;

  public AdminPaymentServiceImpl(
      PaymentRepository paymentRepository,
      OrderRepository orderRepository,
      OrderItemRepository orderItemRepository,
      InventoryRepository inventoryRepository,
      InventoryMovementRepository inventoryMovementRepository,
      RefundRepository refundRepository,
      AuditLogService auditLogService,
      ObjectMapper objectMapper) {
    this.paymentRepository = paymentRepository;
    this.orderRepository = orderRepository;
    this.orderItemRepository = orderItemRepository;
    this.inventoryRepository = inventoryRepository;
    this.inventoryMovementRepository = inventoryMovementRepository;
    this.refundRepository = refundRepository;
    this.auditLogService = auditLogService;
    this.objectMapper = objectMapper;
  }

  @Override
  @Transactional(readOnly = true)
  public Page<AdminPaymentDto> getPayments(
      PaymentStatus status, PaymentMethod method, Instant from, Instant to, Pageable pageable) {
    Specification<Payment> spec = Specification.where(null);

    if (status != null) {
      spec = spec.and((root, q, cb) -> cb.equal(root.get("status"), status));
    }
    if (method != null) {
      spec = spec.and((root, q, cb) -> cb.equal(root.get("method"), method));
    }
    if (from != null) {
      spec = spec.and((root, q, cb) -> cb.greaterThanOrEqualTo(root.get("createdAt"), from));
    }
    if (to != null) {
      spec = spec.and((root, q, cb) -> cb.lessThanOrEqualTo(root.get("createdAt"), to));
    }

    return paymentRepository.findAll(spec, pageable).map(this::toDto);
  }

  @Override
  @Transactional(readOnly = true)
  public AdminPaymentDto getPaymentDetail(Long id) {
    Payment payment =
        paymentRepository
            .findById(id)
            .orElseThrow(() -> new CustomException(ErrorCode.NOT_FOUND, "Payment not found"));
    return toDto(payment);
  }

  @Override
  @Transactional
  public AdminPaymentDto refundPayment(Long id, RefundRequest request, Long adminId, String ip) {
    Payment payment =
        paymentRepository
            .findById(id)
            .orElseThrow(() -> new CustomException(ErrorCode.NOT_FOUND, "Payment not found"));

    if (payment.getStatus() != PaymentStatus.PAID
        && payment.getStatus() != PaymentStatus.PARTIALLY_REFUNDED) {
      throw new CustomException(
          ErrorCode.VALIDATION_ERROR,
          "Only PAID or PARTIALLY_REFUNDED payments can be refunded; current status: "
              + payment.getStatus());
    }

    long alreadyRefunded =
        refundRepository.findByPaymentId(payment.getId()).stream()
            .filter(
                r ->
                    "COMPLETED".equalsIgnoreCase(r.getStatus())
                        || "DONE".equalsIgnoreCase(r.getStatus()))
            .mapToLong(Refund::getAmountCents)
            .sum();

    long remaining = payment.getAmountCents() - alreadyRefunded;
    long reqAmount =
        (request != null && request.amountCents() != null && request.amountCents() > 0)
            ? request.amountCents()
            : remaining;
    String reqReason =
        (request != null && request.reason() != null && !request.reason().isBlank())
            ? request.reason()
            : "Admin manual refund";

    if (reqAmount > remaining) {
      throw new CustomException(
          ErrorCode.VALIDATION_ERROR,
          "Refund amount ("
              + reqAmount
              + " cents) exceeds remaining refundable amount ("
              + remaining
              + " cents)");
    }

    PaymentStatus beforeStatus = payment.getStatus();
    boolean isFullRefund = (reqAmount == remaining);

    // If there were pending refund records created during cancellation, mark them COMPLETED
    List<Refund> pendingRefunds =
        refundRepository.findByPaymentId(payment.getId()).stream()
            .filter(r -> "PENDING".equalsIgnoreCase(r.getStatus()))
            .toList();
    if (!pendingRefunds.isEmpty()) {
      for (Refund r : pendingRefunds) {
        r.setStatus("COMPLETED");
        r.setActorId(adminId);
        refundRepository.save(r);
      }
    } else {
      Refund refund =
          Refund.builder()
              .paymentId(payment.getId())
              .amountCents(reqAmount)
              .reason(reqReason)
              .status("COMPLETED")
              .actorId(adminId)
              .createdAt(Instant.now())
              .build();
      refundRepository.save(refund);
    }

    if (isFullRefund) {
      payment.setStatus(PaymentStatus.REFUNDED);
    } else {
      payment.setStatus(PaymentStatus.PARTIALLY_REFUNDED);
    }
    Payment updated = paymentRepository.save(payment);

    // Return inventory if full refund
    Order order = orderRepository.findById(payment.getOrderId()).orElse(null);
    if (isFullRefund && order != null) {
      order.setPaymentStatus(PaymentStatus.REFUNDED);
      if (order.getStatus() != OrderStatus.CANCELLED) {
        order.setStatus(OrderStatus.CANCELLED);
        order.setCancelledAt(Instant.now());
        order.setCancelReason(request.reason());
      }
      orderRepository.save(order);

      List<OrderItem> items = orderItemRepository.findByOrderId(order.getId());
      for (OrderItem item : items) {
        Inventory inv =
            inventoryRepository
                .findByProductIdAndVariantId(item.getProductId(), item.getVariantId())
                .or(
                    () ->
                        inventoryRepository.findByProductIdAndVariantIdIsNull(item.getProductId()))
                .orElse(null);
        if (inv != null) {
          inventoryRepository.addOnHand(inv.getId(), item.getQuantity());

          inventoryMovementRepository.save(
              InventoryMovement.builder()
                  .inventoryId(inv.getId())
                  .type(InventoryMovementType.RETURN)
                  .quantity(item.getQuantity())
                  .refOrderId(order.getId())
                  .reason("Order refund return #" + order.getOrderNumber())
                  .actorId(adminId)
                  .createdAt(Instant.now())
                  .build());
        }
      }
    }

    auditLogService.log(
        adminId,
        "REFUND_PAYMENT",
        "PAYMENT",
        String.valueOf(id),
        Map.of("status", beforeStatus, "alreadyRefunded", alreadyRefunded),
        Map.of(
            "status", updated.getStatus(),
            "refundAmountCents", request.amountCents(),
            "isFullRefund", isFullRefund),
        ip);

    return toDto(updated);
  }

  @Override
  @Transactional(readOnly = true)
  public byte[] exportPaymentsCsv() {
    List<Payment> payments = paymentRepository.findAll();
    try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
        OutputStreamWriter writer = new OutputStreamWriter(baos, StandardCharsets.UTF_8);
        CSVPrinter printer =
            new CSVPrinter(
                writer,
                CSVFormat.DEFAULT
                    .builder()
                    .setHeader(
                        "ID",
                        "OrderNumber",
                        "Method",
                        "Status",
                        "AmountUSD",
                        "AmountKHR",
                        "BakongHash",
                        "PaidAt",
                        "CreatedAt")
                    .build())) {

      for (Payment p : payments) {
        Order order = orderRepository.findById(p.getOrderId()).orElse(null);
        String orderNum = order != null ? order.getOrderNumber() : "N/A";
        String amountUsd = String.format(java.util.Locale.US, "%.2f", p.getAmountCents() / 100.0);
        String amountKhr = p.getAmountKhr() != null ? String.valueOf(p.getAmountKhr()) : "";

        printer.printRecord(
            p.getId(),
            orderNum,
            p.getMethod(),
            p.getStatus(),
            amountUsd,
            amountKhr,
            p.getBakongHash() != null ? p.getBakongHash() : "",
            p.getPaidAt() != null ? p.getPaidAt().toString() : "",
            p.getCreatedAt().toString());
      }
      printer.flush();
      return baos.toByteArray();
    } catch (Exception e) {
      throw new CustomException(
          ErrorCode.INTERNAL_ERROR, "Failed to generate payments CSV: " + e.getMessage());
    }
  }

  @Override
  @Transactional(readOnly = true)
  public ReconciliationReportDto getReconciliationReport() {
    List<ReconciliationMismatchDto> mismatches = new ArrayList<>();

    // 1. Unpaid confirmed orders
    List<Order> confirmedOrders =
        orderRepository.findAll().stream()
            .filter(
                o ->
                    o.getStatus() == OrderStatus.CONFIRMED
                        || o.getStatus() == OrderStatus.SHIPPED
                        || o.getStatus() == OrderStatus.DELIVERED)
            .toList();

    for (Order o : confirmedOrders) {
      if (o.getPaymentMethod() == PaymentMethod.KHQR
          && o.getPaymentStatus() != PaymentStatus.PAID) {
        mismatches.add(
            new ReconciliationMismatchDto(
                "UNPAID_CONFIRMED_ORDER",
                o.getId(),
                o.getOrderNumber(),
                null,
                "Order is "
                    + o.getStatus()
                    + " but payment status is "
                    + o.getPaymentStatus()
                    + " for method "
                    + o.getPaymentMethod()));
      }
    }

    // 2. Paid payments whose order is cancelled or not confirmed
    List<Payment> paidPayments =
        paymentRepository.findAll().stream()
            .filter(p -> p.getStatus() == PaymentStatus.PAID)
            .toList();

    for (Payment p : paidPayments) {
      Order order = orderRepository.findById(p.getOrderId()).orElse(null);
      if (order != null && order.getStatus() == OrderStatus.CANCELLED) {
        mismatches.add(
            new ReconciliationMismatchDto(
                "PAID_PAYMENT_CANCELLED_ORDER",
                order.getId(),
                order.getOrderNumber(),
                p.getId(),
                "Payment is PAID but order was CANCELLED without full refund"));
      } else if (order != null && !p.getAmountCents().equals(order.getTotalCents())) {
        mismatches.add(
            new ReconciliationMismatchDto(
                "AMOUNT_MISMATCH",
                order.getId(),
                order.getOrderNumber(),
                p.getId(),
                "Payment amount ("
                    + p.getAmountCents()
                    + ") differs from order total ("
                    + order.getTotalCents()
                    + ")"));
      }
    }

    String reportStatus = mismatches.isEmpty() ? "OK" : "MISMATCH_DETECTED";
    return new ReconciliationReportDto(reportStatus, mismatches.size(), mismatches, Instant.now());
  }

  private AdminPaymentDto toDto(Payment p) {
    Order order = orderRepository.findById(p.getOrderId()).orElse(null);
    String orderNum = order != null ? order.getOrderNumber() : null;

    JsonNode rawResponseNode = null;
    if (p.getRawResponse() != null && !p.getRawResponse().isBlank()) {
      try {
        rawResponseNode = objectMapper.readTree(p.getRawResponse());
      } catch (Exception ignored) {
      }
    }

    return new AdminPaymentDto(
        p.getId(),
        p.getOrderId(),
        orderNum,
        p.getMethod(),
        p.getStatus(),
        p.getAmountCents(),
        p.getCurrency(),
        p.getAmountKhr(),
        p.getKhqrMd5(),
        p.getBakongHash(),
        p.getFromAccountId(),
        p.getToAccountId(),
        p.getFailureReason(),
        p.getPaidAt(),
        rawResponseNode,
        p.getCreatedAt());
  }
}
