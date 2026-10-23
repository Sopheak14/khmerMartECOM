package com.khmermart.modules.inventory.repository;

import com.khmermart.modules.inventory.entity.InventoryMovement;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface InventoryMovementRepository extends JpaRepository<InventoryMovement, Long> {
  List<InventoryMovement> findByInventoryIdOrderByCreatedAtDesc(Long inventoryId);
}
