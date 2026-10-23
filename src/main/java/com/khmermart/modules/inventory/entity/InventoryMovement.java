package com.khmermart.modules.inventory.entity;

import com.khmermart.modules.inventory.model.InventoryMovementType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "inventory_movements")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InventoryMovement {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "inventory_id", nullable = false)
  private Long inventoryId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 50)
  private InventoryMovementType type;

  @Column(nullable = false)
  private int quantity;

  @Column private String reason;

  @Column(name = "ref_order_id")
  private Long refOrderId;

  @Column(name = "actor_id")
  private Long actorId;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt = Instant.now();
}
