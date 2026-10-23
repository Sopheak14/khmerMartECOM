package com.khmermart.modules.promotion.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "promotion_products")
@IdClass(PromotionProduct.PromotionProductId.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PromotionProduct {

  @Id
  @Column(name = "promotion_id", nullable = false)
  private Long promotionId;

  @Id
  @Column(name = "product_id", nullable = false)
  private Long productId;

  @Column(name = "sale_price_cents")
  private Long salePriceCents;

  @Getter
  @Setter
  @NoArgsConstructor
  @AllArgsConstructor
  @EqualsAndHashCode
  public static class PromotionProductId implements Serializable {
    private Long promotionId;
    private Long productId;
  }
}
