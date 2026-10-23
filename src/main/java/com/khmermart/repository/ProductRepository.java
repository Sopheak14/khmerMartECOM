package com.khmermart.repository;

import com.khmermart.model.entity.Product;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface ProductRepository
    extends JpaRepository<Product, Long>, JpaSpecificationExecutor<Product> {
  Optional<Product> findBySlug(String slug);

  Optional<Product> findBySku(String sku);

  boolean existsBySlug(String slug);

  boolean existsBySku(String sku);

  Page<Product> findByCategoryIdAndActiveTrue(Long categoryId, Pageable pageable);

  List<Product> findTop10ByCategoryIdAndActiveTrueOrderBySoldCountDesc(Long categoryId);

  List<Product> findTop10ByActiveTrueOrderBySoldCountDesc();

  List<Product> findTop10ByActiveTrueOrderByCreatedAtDesc();
}
