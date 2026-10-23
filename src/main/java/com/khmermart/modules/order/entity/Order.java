package com.khmermart.modules.order.entity;

import com.khmermart.modules.address.dto.AddressDTO;
import com.khmermart.modules.order.model.OrderStatus;
import com.khmermart.modules.payment.model.PaymentMethod;
import com.khmermart.modules.payment.model.PaymentStatus;
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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "orders")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Order {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "order_number", nullable = false, unique = true, length = 100)
  private String orderNumber;

  @Column(name = "user_id", nullable = false)
  private Long userId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 50)
  private OrderStatus status;

  @Enumerated(EnumType.STRING)
  @Column(name = "payment_status", nullable = false, length = 50)
  private PaymentStatus paymentStatus;

  @Enumerated(EnumType.STRING)
  @Column(name = "payment_method", nullable = false, length = 50)
  private PaymentMethod paymentMethod;

  @Column(name = "subtotal_cents", nullable = false)
  private Long subtotalCents;

  @Column(name = "discount_cents", nullable = false)
  private Long discountCents = 0L;

  @Column(name = "shipping_cents", nullable = false)
  private Long shippingCents = 0L;

  @Column(name = "total_cents", nullable = false)
  private Long totalCents;

  @Column(nullable = false, length = 10)
  private String currency = "USD";

  @Column(name = "coupon_code", length = 100)
  private String couponCode;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "shipping_address", columnDefinition = "jsonb", nullable = false)
  private AddressDTO shippingAddress;

  @Column(name = "shipping_method_code", nullable = false, length = 50)
  private String shippingMethodCode;

  @Column(name = "tracking_number", unique = true, length = 100)
  private String trackingNumber;

  @Column(length = 100)
  private String carrier;

  @Column(columnDefinition = "TEXT")
  private String notes;

  @Column(name = "payment_deadline")
  private Instant paymentDeadline;

  @Column(name = "placed_at", nullable = false)
  private Instant placedAt = Instant.now();

  @Column(name = "confirmed_at")
  private Instant confirmedAt;

  @Column(name = "shipped_at")
  private Instant shippedAt;

  @Column(name = "delivered_at")
  private Instant deliveredAt;

  @Column(name = "cancelled_at")
  private Instant cancelledAt;

  @Column(name = "cancel_reason")
  private String cancelReason;

  @Version
  @Column(nullable = false)
  private Long version = 0L;
}
