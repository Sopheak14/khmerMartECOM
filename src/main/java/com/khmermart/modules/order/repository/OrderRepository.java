package com.khmermart.modules.order.repository;

import com.khmermart.modules.order.entity.Order;
import com.khmermart.modules.order.model.OrderStatus;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface OrderRepository
    extends JpaRepository<Order, Long>, JpaSpecificationExecutor<Order> {
  Optional<Order> findByOrderNumber(String orderNumber);

  Optional<Order> findByOrderNumberAndUserId(String orderNumber, Long userId);

  Page<Order> findByUserIdOrderByPlacedAtDesc(Long userId, Pageable pageable);

  Page<Order> findByUserIdAndStatusOrderByPlacedAtDesc(
      Long userId, OrderStatus status, Pageable pageable);

  Optional<Order> findByTrackingNumber(String trackingNumber);
}
