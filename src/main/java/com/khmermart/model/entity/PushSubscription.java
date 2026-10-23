package com.khmermart.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Base64;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "push_subscriptions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PushSubscription {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "user_id", nullable = false)
  private Long userId;

  @Column(nullable = false, unique = true, columnDefinition = "TEXT")
  private String endpoint;

  @Column(nullable = false, columnDefinition = "TEXT")
  private String p256dh;

  @Column(nullable = false, columnDefinition = "TEXT")
  private String auth;

  @Column(name = "created_at", nullable = false, updatable = false)
  @Builder.Default
  private Instant createdAt = Instant.now();

  public byte[] getUserKey() {
    try {
      return Base64.getUrlDecoder().decode(p256dh);
    } catch (IllegalArgumentException e) {
      return Base64.getDecoder().decode(p256dh);
    }
  }

  public byte[] getAuthAsBytes() {
    try {
      return Base64.getUrlDecoder().decode(auth);
    } catch (IllegalArgumentException e) {
      return Base64.getDecoder().decode(auth);
    }
  }
}
