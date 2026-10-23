package com.khmermart.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "shipping_methods")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShippingMethod {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, unique = true, length = 50)
  private String code;

  @Column(nullable = false)
  private String name;

  @Column(name = "name_km", nullable = false)
  private String nameKm;

  @Column(name = "fee_cents", nullable = false)
  private Long feeCents;

  @Column(name = "free_over_cents")
  private Long freeOverCents;

  @Column(name = "eta_days_min", nullable = false)
  private int etaDaysMin;

  @Column(name = "eta_days_max", nullable = false)
  private int etaDaysMax;

  @Column(name = "phnom_penh_only", nullable = false)
  @Builder.Default
  private boolean phnomPenhOnly = false;
}
