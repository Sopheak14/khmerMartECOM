package com.khmermart.modules.product.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "products")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Product {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, unique = true, length = 100)
  private String sku;

  @Column(nullable = false)
  private String name;

  @Column(name = "name_km")
  private String nameKm;

  @Column(nullable = false, unique = true)
  private String slug;

  @Column(columnDefinition = "TEXT")
  private String description;

  @Column(length = 100)
  private String brand;

  @Column(name = "price_cents", nullable = false)
  private Long priceCents;

  @Column(name = "compare_at_cents")
  private Long compareAtCents;

  @Column(name = "category_id")
  private Long categoryId;

  @Column(name = "is_active", nullable = false)
  private boolean active = true;

  @Column(name = "avg_rating", nullable = false, precision = 2, scale = 1)
  private BigDecimal avgRating = BigDecimal.ZERO;

  @Column(name = "review_count", nullable = false)
  private int reviewCount = 0;

  @Column(name = "sold_count", nullable = false)
  private int soldCount = 0;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt = Instant.now();

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt = Instant.now();
}
