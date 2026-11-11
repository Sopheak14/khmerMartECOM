package com.khmermart.service;

import com.khmermart.common.response.SuccessResponse;
import com.khmermart.model.entity.Order;
import com.khmermart.model.enumerate.OrderStatus;
import com.khmermart.model.response.CartDto;
import com.khmermart.model.response.OrderDto;
import java.util.List;
import org.springframework.data.domain.Pageable;

public interface OrderService {
  SuccessResponse<List<OrderDto>> getOrders(Long userId, OrderStatus status, Pageable pageable);

  org.springframework.data.domain.Page<OrderDto> getAdminOrders(
      OrderStatus status,
      com.khmermart.model.enumerate.PaymentStatus paymentStatus,
      java.time.Instant from,
      java.time.Instant to,
      String query,
      org.springframework.data.domain.Pageable pageable);

  OrderDto getOrderByNumber(Long userId, String orderNumber, boolean isStaffOrAdmin);

  /** Customer cancels an UNPAID order. Paid orders must use {@link #requestCancellation}. */
  OrderDto cancelOrder(Long userId, String orderNumber, String reason, String ip);

  OrderDto requestCancellation(Long userId, String orderNumber, String reason, String ip);

  OrderDto adminCancel(String orderNumber, String reason, Long actorId, String ip);

  OrderDto rejectCancelRequest(String orderNumber, String reason, Long actorId, String ip);

  CartDto reorder(Long userId, String orderNumber);

  OrderDto shipOrder(String orderNumber, String carrier);

  OrderDto updateStatus(String orderNumber, OrderStatus newStatus, String note, Long actorId);

  OrderDto toDto(Order order);
}
