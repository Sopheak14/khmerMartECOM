package com.khmermart.repository;

import com.khmermart.model.entity.Inventory;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface InventoryRepository extends JpaRepository<Inventory, Long> {

  Optional<Inventory> findByProductIdAndVariantId(Long productId, Long variantId);

  Optional<Inventory> findByProductIdAndVariantIdIsNull(Long productId);

  List<Inventory> findByProductId(Long productId);

  List<Inventory> findByProductIdIn(List<Long> productIds);

  @Query(
      "SELECT i FROM Inventory i WHERE (i.quantityOnHand - i.quantityReserved) <= i.lowStockThreshold")
  Page<Inventory> findLowStock(Pageable pageable);

  @Modifying
  @Query(
      value =
          """
            UPDATE inventory SET quantity_reserved = quantity_reserved + :qty, updated_at = now()
            WHERE id = :id AND quantity_on_hand - quantity_reserved >= :qty
            """,
      nativeQuery = true)
  int tryReserve(@Param("id") long id, @Param("qty") int qty);

  @Modifying
  @Query(
      value =
          """
            UPDATE inventory SET quantity_reserved = quantity_reserved - :qty, updated_at = now()
            WHERE id = :id AND quantity_reserved >= :qty
            """,
      nativeQuery = true)
  int releaseReserved(@Param("id") long id, @Param("qty") int qty);

  @Modifying
  @Query(
      value =
          """
            UPDATE inventory
            SET quantity_on_hand = quantity_on_hand - :qty,
                quantity_reserved = quantity_reserved - :qty,
                updated_at = now()
            WHERE id = :id AND quantity_reserved >= :qty AND quantity_on_hand >= :qty
            """,
      nativeQuery = true)
  int deductReservedSale(@Param("id") long id, @Param("qty") int qty);
}
