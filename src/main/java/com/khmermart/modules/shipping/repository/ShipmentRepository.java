package com.khmermart.modules.shipping.repository;

import com.khmermart.modules.shipping.entity.Shipment;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ShipmentRepository extends JpaRepository<Shipment, Long> {
  Optional<Shipment> findByOrderId(Long orderId);

  Optional<Shipment> findByTrackingNumber(String trackingNumber);
}
