package com.khmermart.modules.order.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "order_items")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderItem {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "order_id", nullable = false)
  private Long orderId;

  @Column(name = "product_id", nullable = false)
  private Long productId;

  @Column(name = "variant_id")
  private Long variantId;

  @Column(name = "product_name_snapshot", nullable = false)
  private String productNameSnapshot;

  @Column(name = "sku_snapshot", nullable = false, length = 100)
  private String skuSnapshot;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "attributes_snapshot", columnDefinition = "jsonb")
  private Map<String, String> attributesSnapshot;

  @Column(name = "image_url_snapshot", length = 500)
  private String imageUrlSnapshot;

  @Column(name = "unit_price_cents", nullable = false)
  private Long unitPriceCents;

  @Column(nullable = false)
  private int quantity;

  @Column(name = "line_total_cents", nullable = false)
  private Long lineTotalCents;
}
