package com.khmermart.model.entity;

import com.khmermart.model.enumerate.PaymentMethod;
import com.khmermart.model.enumerate.PaymentStatus;
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
@Table(name = "payments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Payment {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "order_id", nullable = false)
  private Long orderId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 50)
  private PaymentMethod method;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 50)
  private PaymentStatus status;

  @Column(name = "amount_cents", nullable = false)
  private Long amountCents;

  @Column(nullable = false, length = 10)
  @Builder.Default
  private String currency = "USD";

  @Column(name = "amount_khr")
  private Long amountKhr;

  @Column(name = "khqr_string", columnDefinition = "TEXT")
  private String khqrString;

  @Column(name = "khqr_md5", unique = true, length = 100)
  private String khqrMd5;

  @Column(name = "qr_expires_at")
  private Instant qrExpiresAt;

  @Column(name = "last_checked_at")
  private Instant lastCheckedAt;

  @Column(name = "check_count", nullable = false)
  @Builder.Default
  private int checkCount = 0;

  @Column(name = "bakong_hash", unique = true)
  private String bakongHash;

  @Column(name = "from_account_id", length = 100)
  private String fromAccountId;

  @Column(name = "to_account_id", length = 100)
  private String toAccountId;

  @Column(name = "external_ref", length = 100)
  private String externalRef;

  @Column(name = "failure_reason")
  private String failureReason;

  @Column(name = "paid_at")
  private Instant paidAt;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "raw_response", columnDefinition = "jsonb")
  private String rawResponse;

  @Version
  @Column(nullable = false)
  @Builder.Default
  private Long version = 0L;

  @Column(name = "created_at", nullable = false, updatable = false)
  @Builder.Default
  private Instant createdAt = Instant.now();
}
