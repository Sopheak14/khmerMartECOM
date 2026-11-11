package com.khmermart.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
@Table(name = "refunds")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Refund {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "payment_id", nullable = false)
  private Long paymentId;

  @Column(name = "amount_cents", nullable = false)
  private Long amountCents;

  @Column(length = 255)
  private String reason;

  @Column(length = 100)
  private String reference;

  @Column(nullable = false, length = 50)
  private String status;

  @Column(name = "actor_id")
  private Long actorId;

  @Column(name = "payer_account", length = 100)
  private String payerAccount;

  @Column(name = "requested_at")
  private Instant requestedAt;

  @Column(name = "approved_by")
  private Long approvedBy;

  @Column(name = "completed_at")
  private Instant completedAt;

  @Column(name = "completed_by")
  private Long completedBy;

  @Column(name = "bank_reference", length = 64)
  private String bankReference;

  @Column(length = 500)
  private String note;

  @Column(name = "created_at", nullable = false, updatable = false)
  @Builder.Default
  private Instant createdAt = Instant.now();
}
