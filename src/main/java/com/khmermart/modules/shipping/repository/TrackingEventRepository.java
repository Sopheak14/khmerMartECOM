package com.khmermart.modules.shipping.repository;

import com.khmermart.modules.shipping.entity.TrackingEvent;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TrackingEventRepository extends JpaRepository<TrackingEvent, Long> {
  List<TrackingEvent> findByShipmentIdOrderByCreatedAtAsc(Long shipmentId);
}
