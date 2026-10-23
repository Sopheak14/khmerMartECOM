package com.khmermart.model.entity;

import com.khmermart.model.enumerate.PromotionType;
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
@Table(name = "promotions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Promotion {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false)
  private String name;

  @Column(name = "name_km")
  private String nameKm;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 50)
  private PromotionType type;

  @Column(name = "discount_percent")
  private Integer discountPercent;

  @Column(name = "threshold_cents")
  private Long thresholdCents;

  @Column(name = "starts_at", nullable = false)
  private Instant startsAt;

  @Column(name = "ends_at", nullable = false)
  private Instant endsAt;

  @Column(name = "banner_image", length = 500)
  private String bannerImage;

  @Column private String link;

  @Column(name = "is_active", nullable = false)
  @Builder.Default
  private boolean active = true;
}
