package com.khmermart.model.entity;

import com.khmermart.model.enumerate.ShipmentStatus;
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
@Table(name = "shipments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Shipment {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "order_id", nullable = false)
  private Long orderId;

  @Column(nullable = false, length = 100)
  private String carrier;

  @Column(name = "tracking_number", nullable = false, unique = true, length = 100)
  private String trackingNumber;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 50)
  private ShipmentStatus status;

  @Column(name = "estimated_delivery")
  private Instant estimatedDelivery;
}
