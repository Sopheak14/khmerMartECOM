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

  OrderDto getOrderByNumber(Long userId, String orderNumber, boolean isStaffOrAdmin);

  OrderDto cancelOrder(Long userId, String orderNumber, String reason);

  CartDto reorder(Long userId, String orderNumber);

  OrderDto shipOrder(String orderNumber, String carrier);

  OrderDto updateStatus(String orderNumber, OrderStatus newStatus, String note, Long actorId);

  OrderDto processRefund(Long adminId, Long paymentId, String reference);

  OrderDto toDto(Order order);
}
