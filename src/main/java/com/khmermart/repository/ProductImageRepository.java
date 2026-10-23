package com.khmermart.repository;

import com.khmermart.model.entity.ProductImage;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProductImageRepository extends JpaRepository<ProductImage, Long> {
  List<ProductImage> findByProductIdOrderBySortOrderAsc(Long productId);

  List<ProductImage> findByProductIdInOrderBySortOrderAsc(List<Long> productIds);

  void deleteByProductId(Long productId);
}
