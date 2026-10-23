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
@Table(name = "categories")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Category {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false)
  private String name;

  @Column(name = "name_km")
  private String nameKm;

  @Column(nullable = false, unique = true)
  private String slug;

  @Column(name = "parent_id")
  private Long parentId;

  @Column(name = "image_url", length = 500)
  private String imageUrl;

  @Column(name = "sort_order", nullable = false)
  @Builder.Default
  private int sortOrder = 0;
}
