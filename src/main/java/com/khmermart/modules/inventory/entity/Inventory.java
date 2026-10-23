package com.khmermart.modules.inventory.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "inventory")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Inventory {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "product_id", nullable = false)
  private Long productId;

  @Column(name = "variant_id")
  private Long variantId;

  @Column(name = "quantity_on_hand", nullable = false)
  private int quantityOnHand = 0;

  @Column(name = "quantity_reserved", nullable = false)
  private int quantityReserved = 0;

  @Column(name = "low_stock_threshold", nullable = false)
  private int lowStockThreshold = 5;

  @Column(name = "low_stock_alerted", nullable = false)
  private boolean lowStockAlerted = false;

  @Version
  @Column(nullable = false)
  private Long version = 0L;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt = Instant.now();

  public int getAvailable() {
    return quantityOnHand - quantityReserved;
  }
}
