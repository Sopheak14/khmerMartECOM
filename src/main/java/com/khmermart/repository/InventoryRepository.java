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

  /**
   * Fresh available stock straight from the DB (a scalar query never returns the cached entity).
   */
  @Query("SELECT i.quantityOnHand - i.quantityReserved FROM Inventory i WHERE i.id = :id")
  int availableOf(@Param("id") long id);

  // Stock is only ever changed by the atomic statements below, never by read-modify-write of a
  // managed Inventory. Each bumps `version` so a stale entity save fails instead of overwriting.

  @Modifying(flushAutomatically = true)
  @Query(
      value =
          """
            UPDATE inventory
            SET quantity_reserved = quantity_reserved + :qty, version = version + 1, updated_at = now()
            WHERE id = :id AND quantity_on_hand - quantity_reserved >= :qty
            """,
      nativeQuery = true)
  int tryReserve(@Param("id") long id, @Param("qty") int qty);

  @Modifying(flushAutomatically = true)
  @Query(
      value =
          """
            UPDATE inventory
            SET quantity_reserved = GREATEST(0, quantity_reserved - :qty),
                version = version + 1, updated_at = now()
            WHERE id = :id
            """,
      nativeQuery = true)
  int releaseReserved(@Param("id") long id, @Param("qty") int qty);

  @Modifying(flushAutomatically = true)
  @Query(
      value =
          """
            UPDATE inventory
            SET quantity_on_hand = GREATEST(0, quantity_on_hand - :qty),
                quantity_reserved = GREATEST(0, quantity_reserved - :qty),
                version = version + 1, updated_at = now()
            WHERE id = :id
            """,
      nativeQuery = true)
  int deductReservedSale(@Param("id") long id, @Param("qty") int qty);

  @Modifying(flushAutomatically = true)
  @Query(
      value =
          """
            UPDATE inventory
            SET quantity_on_hand = quantity_on_hand + :qty, version = version + 1, updated_at = now()
            WHERE id = :id
            """,
      nativeQuery = true)
  int addOnHand(@Param("id") long id, @Param("qty") int qty);

  @Modifying(flushAutomatically = true)
  @Query(
      value =
          """
            UPDATE inventory
            SET quantity_on_hand = quantity_on_hand + :qty, low_stock_alerted = false,
                version = version + 1, updated_at = now()
            WHERE id = :id
            """,
      nativeQuery = true)
  int restock(@Param("id") long id, @Param("qty") int qty);

  @Modifying(flushAutomatically = true)
  @Query(
      value =
          """
            UPDATE inventory
            SET quantity_on_hand = quantity_on_hand + :delta, version = version + 1, updated_at = now()
            WHERE id = :id AND quantity_on_hand + :delta >= 0
            """,
      nativeQuery = true)
  int tryAdjustOnHand(@Param("id") long id, @Param("delta") int delta);

  @Modifying(flushAutomatically = true)
  @Query(
      value =
          """
            UPDATE inventory
            SET low_stock_alerted = true, version = version + 1, updated_at = now()
            WHERE id = :id AND low_stock_alerted = false
            """,
      nativeQuery = true)
  int markLowStockAlerted(@Param("id") long id);
}
