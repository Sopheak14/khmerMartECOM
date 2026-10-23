package com.khmermart.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.khmermart.common.exception.CustomException;
import com.khmermart.common.exception.ErrorCode;
import com.khmermart.common.response.PaginationResponse;
import com.khmermart.common.response.SuccessResponse;
import com.khmermart.model.entity.Cart;
import com.khmermart.model.entity.Inventory;
import com.khmermart.model.entity.InventoryMovement;
import com.khmermart.model.entity.Order;
import com.khmermart.model.entity.OrderItem;
import com.khmermart.model.entity.OrderStatusHistory;
import com.khmermart.model.entity.Payment;
import com.khmermart.model.entity.Product;
import com.khmermart.model.entity.ProductImage;
import com.khmermart.model.entity.Refund;
import com.khmermart.model.entity.ShippingMethod;
import com.khmermart.model.enumerate.CouponRedemptionStatus;
import com.khmermart.model.enumerate.InventoryMovementType;
import com.khmermart.model.enumerate.OrderStatus;
import com.khmermart.model.enumerate.PaymentMethod;
import com.khmermart.model.enumerate.PaymentStatus;
import com.khmermart.model.request.CartItemRequest;
import com.khmermart.model.response.AddressDto;
import com.khmermart.model.response.CartDto;
import com.khmermart.model.response.OrderDto;
import com.khmermart.model.response.OrderItemDto;
import com.khmermart.model.response.OrderTimelineDto;
import com.khmermart.model.response.ShippingMethodDto;
import com.khmermart.repository.CouponRedemptionRepository;
import com.khmermart.repository.CouponRepository;
import com.khmermart.repository.InventoryMovementRepository;
import com.khmermart.repository.InventoryRepository;
import com.khmermart.repository.OrderItemRepository;
import com.khmermart.repository.OrderRepository;
import com.khmermart.repository.OrderStatusHistoryRepository;
import com.khmermart.repository.PaymentRepository;
import com.khmermart.repository.ProductImageRepository;
import com.khmermart.repository.ProductRepository;
import com.khmermart.repository.ProductVariantRepository;
import com.khmermart.repository.RefundRepository;
import com.khmermart.repository.ShippingMethodRepository;
import com.khmermart.service.CartService;
import com.khmermart.service.OrderService;
import com.khmermart.service.OrderStateMachine;
import com.khmermart.service.ShippingService;
import com.khmermart.util.ApiResponseUtil;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderServiceImpl implements OrderService {

  private final OrderRepository orderRepository;
  private final OrderItemRepository orderItemRepository;
  private final OrderStatusHistoryRepository orderStatusHistoryRepository;
  private final PaymentRepository paymentRepository;
  private final InventoryRepository inventoryRepository;
  private final InventoryMovementRepository movementRepository;
  private final ProductRepository productRepository;
  private final ProductVariantRepository variantRepository;
  private final ProductImageRepository imageRepository;
  private final CouponRepository couponRepository;
  private final CouponRedemptionRepository couponRedemptionRepository;
  private final RefundRepository refundRepository;
  private final ShippingMethodRepository shippingMethodRepository;
  private final ShippingService shippingService;
  private final CartService cartService;
  private final OrderStateMachine orderStateMachine;
  private final ObjectMapper objectMapper;

  public OrderServiceImpl(
      OrderRepository orderRepository,
      OrderItemRepository orderItemRepository,
      OrderStatusHistoryRepository orderStatusHistoryRepository,
      PaymentRepository paymentRepository,
      InventoryRepository inventoryRepository,
      InventoryMovementRepository movementRepository,
      ProductRepository productRepository,
      ProductVariantRepository variantRepository,
      ProductImageRepository imageRepository,
      CouponRepository couponRepository,
      CouponRedemptionRepository couponRedemptionRepository,
      RefundRepository refundRepository,
      ShippingMethodRepository shippingMethodRepository,
      ShippingService shippingService,
      CartService cartService,
      OrderStateMachine orderStateMachine,
      ObjectMapper objectMapper) {
    this.orderRepository = orderRepository;
    this.orderItemRepository = orderItemRepository;
    this.orderStatusHistoryRepository = orderStatusHistoryRepository;
    this.paymentRepository = paymentRepository;
    this.inventoryRepository = inventoryRepository;
    this.movementRepository = movementRepository;
    this.productRepository = productRepository;
    this.variantRepository = variantRepository;
    this.imageRepository = imageRepository;
    this.couponRepository = couponRepository;
    this.couponRedemptionRepository = couponRedemptionRepository;
    this.refundRepository = refundRepository;
    this.shippingMethodRepository = shippingMethodRepository;
    this.shippingService = shippingService;
    this.cartService = cartService;
    this.orderStateMachine = orderStateMachine;
    this.objectMapper = objectMapper;
  }

  @Override
  @Transactional(readOnly = true)
  public SuccessResponse<List<OrderDto>> getOrders(
      Long userId, OrderStatus status, Pageable pageable) {
    Page<Order> page;
    if (status != null) {
      page = orderRepository.findByUserIdAndStatus(userId, status, pageable);
    } else {
      page = orderRepository.findByUserId(userId, pageable);
    }

    List<OrderDto> dtos = page.getContent().stream().map(this::toDto).toList();
    PaginationResponse meta =
        new PaginationResponse(
            page.getNumber() + 1, page.getSize(), page.getTotalElements(), page.getTotalPages());
    return ApiResponseUtil.success(dtos, meta);
  }

  @Override
  @Transactional(readOnly = true)
  public OrderDto getOrderByNumber(Long userId, String orderNumber, boolean isStaffOrAdmin) {
    Order order =
        orderRepository
            .findByOrderNumber(orderNumber)
            .orElseThrow(() -> new CustomException(ErrorCode.NOT_FOUND, "Order not found"));

    if (userId != null && !order.getUserId().equals(userId) && !isStaffOrAdmin) {
      throw new CustomException(ErrorCode.FORBIDDEN, "Access denied");
    }

    return toDto(order);
  }

  @Override
  @Transactional
  public OrderDto cancelOrder(Long userId, String orderNumber, String reason) {
    Order order =
        orderRepository
            .findByOrderNumber(orderNumber)
            .orElseThrow(() -> new CustomException(ErrorCode.NOT_FOUND, "Order not found"));

    if (userId != null && !order.getUserId().equals(userId)) {
      throw new CustomException(ErrorCode.FORBIDDEN, "Access denied");
    }

    if (order.getStatus() == OrderStatus.SHIPPED
        || order.getStatus() == OrderStatus.OUT_FOR_DELIVERY
        || order.getStatus() == OrderStatus.DELIVERED) {
      throw new CustomException(
          ErrorCode.INVALID_STATUS_TRANSITION,
          "Cannot cancel order that has already shipped or completed: " + order.getStatus());
    }

    if (order.getStatus() == OrderStatus.CANCELLED
        || order.getStatus() == OrderStatus.PAYMENT_EXPIRED) {
      throw new CustomException(
          ErrorCode.INVALID_STATUS_TRANSITION,
          "Order is already cancelled or expired: " + order.getStatus());
    }

    OrderStatus fromStatus = order.getStatus();
    Instant now = Instant.now();

    order.setStatus(OrderStatus.CANCELLED);
    order.setCancelledAt(now);
    order.setCancelReason(reason != null ? reason : "Cancelled by customer");
    order = orderRepository.save(order);

    OrderStatusHistory history =
        OrderStatusHistory.builder()
            .orderId(order.getId())
            .fromStatus(fromStatus.name())
            .toStatus(OrderStatus.CANCELLED.name())
            .note(reason != null ? reason : "Order cancelled")
            .actorId(userId)
            .createdAt(now)
            .build();
    orderStatusHistoryRepository.save(history);

    // Release stock
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
        InventoryMovementType movementType;
        if (order.getPaymentStatus() == PaymentStatus.PAID) {
          inv.setQuantityOnHand(inv.getQuantityOnHand() + item.getQuantity());
          movementType = InventoryMovementType.RETURN;
        } else {
          inv.setQuantityReserved(Math.max(0, inv.getQuantityReserved() - item.getQuantity()));
          movementType = InventoryMovementType.RELEASE;
        }
        inventoryRepository.save(inv);

        InventoryMovement movement =
            InventoryMovement.builder()
                .inventoryId(inv.getId())
                .type(movementType)
                .quantity(item.getQuantity())
                .reason("Order cancelled: " + order.getOrderNumber())
                .actorId(userId)
                .refOrderId(order.getId())
                .createdAt(now)
                .build();
        movementRepository.save(movement);
      }
    }

    // Release coupon
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

    // If paid, create manual refund record
    if (order.getPaymentStatus() == PaymentStatus.PAID) {
      Payment payment =
          paymentRepository.findFirstByOrderIdOrderByIdDesc(order.getId()).orElse(null);
      if (payment != null) {
        Refund refund =
            Refund.builder()
                .paymentId(payment.getId())
                .amountCents(payment.getAmountCents())
                .reason(reason != null ? reason : "Order cancelled")
                .status("PENDING")
                .actorId(userId)
                .createdAt(now)
                .build();
        refundRepository.save(refund);
      }
    }

    return toDto(order);
  }

  @Override
  @Transactional
  public OrderDto processRefund(Long adminId, Long paymentId, String reference) {
    Payment payment =
        paymentRepository
            .findById(paymentId)
            .orElseThrow(() -> new CustomException(ErrorCode.NOT_FOUND, "Payment not found"));

    Order order =
        orderRepository
            .findById(payment.getOrderId())
            .orElseThrow(() -> new CustomException(ErrorCode.NOT_FOUND, "Order not found"));

    payment.setStatus(PaymentStatus.REFUNDED);
    paymentRepository.save(payment);

    order.setPaymentStatus(PaymentStatus.REFUNDED);
    orderRepository.save(order);

    List<Refund> refunds = refundRepository.findByPaymentId(payment.getId());
    if (refunds.isEmpty()) {
      Refund refund =
          Refund.builder()
              .paymentId(payment.getId())
              .amountCents(payment.getAmountCents())
              .reason("Manual refund")
              .status("COMPLETED")
              .reference(
                  reference != null
                      ? reference
                      : "REFUND-" + UUID.randomUUID().toString().substring(0, 8))
              .actorId(adminId)
              .createdAt(Instant.now())
              .build();
      refundRepository.save(refund);
    } else {
      for (Refund r : refunds) {
        if ("PENDING".equalsIgnoreCase(r.getStatus())) {
          r.setStatus("COMPLETED");
          r.setActorId(adminId);
          if (reference != null) {
            r.setReference(reference);
          } else if (r.getReference() == null) {
            r.setReference("REFUND-" + UUID.randomUUID().toString().substring(0, 8));
          }
          refundRepository.save(r);
        }
      }
    }

    return toDto(order);
  }

  @Override
  @Transactional
  public CartDto reorder(Long userId, String orderNumber) {
    Order order =
        orderRepository
            .findByOrderNumber(orderNumber)
            .orElseThrow(() -> new CustomException(ErrorCode.NOT_FOUND, "Order not found"));

    if (userId != null && !order.getUserId().equals(userId)) {
      throw new CustomException(ErrorCode.FORBIDDEN, "Access denied");
    }

    Cart cart = cartService.getOrCreateCart(userId, null);
    List<OrderItem> items = orderItemRepository.findByOrderId(order.getId());

    for (OrderItem item : items) {
      productRepository
          .findById(item.getProductId())
          .filter(Product::isActive)
          .ifPresent(
              p -> {
                cartService.addItem(
                    cart,
                    new CartItemRequest(
                        item.getProductId(), item.getVariantId(), item.getQuantity()));
              });
    }

    return cartService.getCartDto(cart);
  }

  @Override
  @Transactional
  public OrderDto shipOrder(String orderNumber, String carrier) {
    Order order =
        orderRepository
            .findByOrderNumber(orderNumber)
            .orElseThrow(() -> new CustomException(ErrorCode.NOT_FOUND, "Order not found"));

    OrderStatus fromStatus = order.getStatus();
    if (!orderStateMachine.canTransition(fromStatus, OrderStatus.SHIPPED)) {
      throw new CustomException(
          ErrorCode.INVALID_STATUS_TRANSITION,
          "Cannot transition order from " + fromStatus + " to SHIPPED");
    }

    Instant now = Instant.now();
    order.setStatus(OrderStatus.SHIPPED);
    order.setShippedAt(now);
    order.setCarrier(carrier != null && !carrier.isBlank() ? carrier : "KhmerMart Express");
    order.setTrackingNumber("TRK-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
    order = orderRepository.save(order);

    OrderStatusHistory history =
        OrderStatusHistory.builder()
            .orderId(order.getId())
            .fromStatus(fromStatus.name())
            .toStatus(OrderStatus.SHIPPED.name())
            .note("Order marked SHIPPED with carrier " + order.getCarrier())
            .createdAt(now)
            .build();
    orderStatusHistoryRepository.save(history);

    // COD rule: COD order converted RESERVE -> SALE when SHIPPED
    if (order.getPaymentMethod() == PaymentMethod.COD) {
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
          inv.setQuantityOnHand(Math.max(0, inv.getQuantityOnHand() - item.getQuantity()));
          inv.setQuantityReserved(Math.max(0, inv.getQuantityReserved() - item.getQuantity()));
          inventoryRepository.save(inv);

          InventoryMovement movement =
              InventoryMovement.builder()
                  .inventoryId(inv.getId())
                  .type(InventoryMovementType.SALE)
                  .quantity(item.getQuantity())
                  .reason("COD order shipped: " + order.getOrderNumber())
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
    }

    return toDto(order);
  }

  @Override
  @Transactional
  public OrderDto updateStatus(
      String orderNumber, OrderStatus newStatus, String note, Long actorId) {
    Order order =
        orderRepository
            .findByOrderNumber(orderNumber)
            .orElseThrow(() -> new CustomException(ErrorCode.NOT_FOUND, "Order not found"));

    OrderStatus fromStatus = order.getStatus();
    if (!orderStateMachine.canTransition(fromStatus, newStatus)) {
      throw new CustomException(
          ErrorCode.INVALID_STATUS_TRANSITION,
          "Cannot transition order from " + fromStatus + " to " + newStatus);
    }

    Instant now = Instant.now();
    order.setStatus(newStatus);
    if (newStatus == OrderStatus.DELIVERED) {
      order.setDeliveredAt(now);
      if (order.getPaymentMethod() == PaymentMethod.COD) {
        order.setPaymentStatus(PaymentStatus.PAID);
        paymentRepository
            .findFirstByOrderIdOrderByIdDesc(order.getId())
            .ifPresent(
                p -> {
                  p.setStatus(PaymentStatus.PAID);
                  p.setPaidAt(now);
                  paymentRepository.save(p);
                });
      }
    } else if (newStatus == OrderStatus.CANCELLED) {
      order.setCancelledAt(now);
      order.setCancelReason(note);
    }
    order = orderRepository.save(order);

    OrderStatusHistory history =
        OrderStatusHistory.builder()
            .orderId(order.getId())
            .fromStatus(fromStatus.name())
            .toStatus(newStatus.name())
            .note(note != null ? note : "Status updated to " + newStatus)
            .actorId(actorId)
            .createdAt(now)
            .build();
    orderStatusHistoryRepository.save(history);

    return toDto(order);
  }

  @Override
  @Transactional(readOnly = true)
  public OrderDto toDto(Order order) {
    List<OrderItem> items = orderItemRepository.findByOrderId(order.getId());
    List<OrderItemDto> itemDtos = new ArrayList<>();

    for (OrderItem item : items) {
      Product product = productRepository.findById(item.getProductId()).orElse(null);
      String slug = product != null ? product.getSlug() : "";
      String name = product != null ? product.getName() : item.getProductNameSnapshot();

      String imageUrl = item.getImageUrlSnapshot();
      if (imageUrl == null && product != null) {
        imageUrl =
            imageRepository.findByProductIdOrderBySortOrderAsc(product.getId()).stream()
                .findFirst()
                .map(ProductImage::getUrl)
                .orElse(null);
      }

      Map<String, String> attrs = null;
      if (item.getAttributesSnapshot() != null) {
        try {
          attrs = objectMapper.readValue(item.getAttributesSnapshot(), Map.class);
        } catch (Exception ignored) {
        }
      }

      boolean canReview = order.getStatus() == OrderStatus.DELIVERED;

      itemDtos.add(
          new OrderItemDto(
              item.getId(),
              item.getProductId(),
              slug,
              name,
              imageUrl,
              item.getSkuSnapshot(),
              attrs,
              item.getUnitPriceCents(),
              item.getQuantity(),
              item.getLineTotalCents(),
              canReview));
    }

    AddressDto addressDto = null;
    if (order.getShippingAddress() != null) {
      try {
        addressDto = objectMapper.readValue(order.getShippingAddress(), AddressDto.class);
      } catch (Exception ignored) {
      }
    }

    ShippingMethod shippingMethod =
        shippingMethodRepository.findByCode(order.getShippingMethodCode()).orElse(null);
    ShippingMethodDto shippingMethodDto =
        shippingMethod != null ? shippingService.toDto(shippingMethod) : null;

    List<OrderStatusHistory> histories =
        orderStatusHistoryRepository.findByOrderIdOrderByCreatedAtAsc(order.getId());
    List<OrderTimelineDto> timeline = new ArrayList<>();
    for (OrderStatusHistory h : histories) {
      timeline.add(new OrderTimelineDto(h.getToStatus(), h.getNote(), h.getCreatedAt()));
    }
    if (timeline.isEmpty()) {
      timeline.add(
          new OrderTimelineDto(order.getStatus().name(), "Order placed", order.getPlacedAt()));
    }

    boolean canCancel =
        order.getStatus() == OrderStatus.PENDING_PAYMENT
            || order.getStatus() == OrderStatus.CONFIRMED
            || order.getStatus() == OrderStatus.PROCESSING;

    boolean canReview = order.getStatus() == OrderStatus.DELIVERED;

    return new OrderDto(
        order.getId(),
        order.getOrderNumber(),
        order.getStatus(),
        order.getPaymentStatus(),
        order.getPaymentMethod(),
        itemDtos,
        order.getSubtotalCents(),
        order.getDiscountCents(),
        order.getShippingCents(),
        order.getTotalCents(),
        order.getCouponCode(),
        addressDto,
        shippingMethodDto,
        order.getTrackingNumber(),
        order.getCarrier(),
        order.getPaymentDeadline(),
        order.getPlacedAt(),
        order.getConfirmedAt(),
        order.getShippedAt(),
        order.getDeliveredAt(),
        order.getCancelledAt(),
        order.getCancelReason(),
        timeline,
        canCancel,
        canReview,
        "/api/orders/" + order.getOrderNumber() + "/invoice.pdf");
  }
}
