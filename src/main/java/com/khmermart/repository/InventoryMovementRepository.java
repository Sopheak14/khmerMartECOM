package com.khmermart.repository;

import com.khmermart.model.entity.InventoryMovement;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface InventoryMovementRepository extends JpaRepository<InventoryMovement, Long> {
  List<InventoryMovement> findByRefOrderId(Long refOrderId);
}
