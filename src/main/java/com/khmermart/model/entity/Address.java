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
@Table(name = "addresses")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Address {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "user_id", nullable = false)
  private Long userId;

  @Column(nullable = false, length = 100)
  private String label;

  @Column(name = "recipient_name", nullable = false)
  private String recipientName;

  @Column(nullable = false, length = 50)
  private String phone;

  @Column(nullable = false)
  private String line1;

  @Column private String line2;

  @Column(nullable = false, length = 100)
  private String district;

  @Column(nullable = false, length = 100)
  private String province;

  @Column(name = "postal_code", length = 50)
  private String postalCode;

  @Column(name = "is_default", nullable = false)
  @Builder.Default
  private boolean isDefault = false;
}
