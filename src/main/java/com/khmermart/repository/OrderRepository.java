package com.khmermart.repository;

import com.khmermart.model.entity.Order;
import com.khmermart.model.enumerate.OrderStatus;
import java.util.List;
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

  Page<Order> findByUserId(Long userId, Pageable pageable);

  Page<Order> findByUserIdAndStatus(Long userId, OrderStatus status, Pageable pageable);

  List<Order> findByStatus(OrderStatus status);
}
