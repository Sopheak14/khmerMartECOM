package com.khmermart.model.entity;

import com.khmermart.model.enumerate.CouponType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
@Table(name = "coupons")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Coupon {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, unique = true, length = 100)
  private String code;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 50)
  private CouponType type;

  @Column(nullable = false)
  private Long value;

  @Column(columnDefinition = "TEXT")
  private String description;

  @Column(name = "min_order_cents")
  private Long minOrderCents;

  @Column(name = "max_discount_cents")
  private Long maxDiscountCents;

  @Column(name = "starts_at")
  private Instant startsAt;

  @Column(name = "ends_at")
  private Instant endsAt;

  @Column(name = "usage_limit")
  private Integer usageLimit;

  @Column(name = "per_user_limit", nullable = false)
  @Builder.Default
  private int perUserLimit = 1;

  @Column(name = "used_count", nullable = false)
  @Builder.Default
  private int usedCount = 0;

  @Column(name = "is_active", nullable = false)
  @Builder.Default
  private boolean active = true;

  @Column(name = "applies_to_category_id")
  private Long appliesToCategoryId;

  @Column(name = "applies_to_product_id")
  private Long appliesToProductId;

  @Version
  @Column(nullable = false)
  @Builder.Default
  private Long version = 0L;
}
