package com.khmermart.modules.category.repository;

import com.khmermart.modules.category.entity.Category;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface CategoryRepository extends JpaRepository<Category, Long> {
  Optional<Category> findBySlug(String slug);

  List<Category> findByParentIdIsNullOrderBySortOrderAsc();

  List<Category> findByParentIdOrderBySortOrderAsc(Long parentId);

  List<Category> findAllByOrderBySortOrderAsc();

  boolean existsBySlug(String slug);

  @Query(
      "SELECT p.categoryId, COUNT(p.id) FROM Product p WHERE p.active = true GROUP BY p.categoryId")
  List<Object[]> countActiveProductsPerCategory();
}
