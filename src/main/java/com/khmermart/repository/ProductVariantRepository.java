package com.khmermart.repository;

import com.khmermart.model.entity.ProductVariant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProductVariantRepository extends JpaRepository<ProductVariant, Long> {
  List<ProductVariant> findByProductIdAndActiveTrue(Long productId);

  List<ProductVariant> findByProductIdInAndActiveTrue(List<Long> productIds);

  Optional<ProductVariant> findBySku(String sku);
}
