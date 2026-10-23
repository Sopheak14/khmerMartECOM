package com.khmermart.modules.category.service;

import com.khmermart.common.api.ApiException;
import com.khmermart.common.api.ErrorCode;
import com.khmermart.modules.category.dto.CategoryDTO;
import com.khmermart.modules.category.entity.Category;
import com.khmermart.modules.category.repository.CategoryRepository;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class CategoryService {

  private final CategoryRepository categoryRepository;

  public CategoryService(CategoryRepository categoryRepository) {
    this.categoryRepository = categoryRepository;
  }

  @Cacheable(value = "categories", key = "'tree'")
  public List<CategoryDTO> getCategoryTree() {
    List<Category> allCategories = categoryRepository.findAllByOrderBySortOrderAsc();
    Map<Long, Long> productCounts = getProductCountsMap();

    Map<Long, List<Category>> childrenMap = new HashMap<>();
    List<Category> rootCategories = new ArrayList<>();

    for (Category category : allCategories) {
      if (category.getParentId() == null) {
        rootCategories.add(category);
      } else {
        childrenMap.computeIfAbsent(category.getParentId(), k -> new ArrayList<>()).add(category);
      }
    }

    List<CategoryDTO> result = new ArrayList<>();
    for (Category root : rootCategories) {
      result.add(buildDTOWithChildren(root, childrenMap, productCounts));
    }
    return result;
  }

  public CategoryDTO getCategoryBySlug(String slug) {
    Category category =
        categoryRepository
            .findBySlug(slug)
            .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Category not found"));

    List<Category> allCategories = categoryRepository.findAllByOrderBySortOrderAsc();
    Map<Long, Long> productCounts = getProductCountsMap();

    Map<Long, List<Category>> childrenMap = new HashMap<>();
    for (Category c : allCategories) {
      if (c.getParentId() != null) {
        childrenMap.computeIfAbsent(c.getParentId(), k -> new ArrayList<>()).add(c);
      }
    }

    return buildDTOWithChildren(category, childrenMap, productCounts);
  }

  public CategoryDTO getCategoryById(Long id) {
    if (id == null) return null;
    Category category = categoryRepository.findById(id).orElse(null);
    if (category == null) return null;
    return new CategoryDTO(
        category.getId(),
        category.getName(),
        category.getNameKm(),
        category.getSlug(),
        category.getImageUrl(),
        category.getParentId(),
        List.of(),
        0);
  }

  private CategoryDTO buildDTOWithChildren(
      Category category,
      Map<Long, List<Category>> childrenMap,
      Map<Long, Long> productCounts) {
    List<Category> children = childrenMap.getOrDefault(category.getId(), List.of());
    List<CategoryDTO> childDTOs = new ArrayList<>();
    for (Category child : children) {
      childDTOs.add(buildDTOWithChildren(child, childrenMap, productCounts));
    }
    long count = productCounts.getOrDefault(category.getId(), 0L);
    return new CategoryDTO(
        category.getId(),
        category.getName(),
        category.getNameKm(),
        category.getSlug(),
        category.getImageUrl(),
        category.getParentId(),
        childDTOs,
        count);
  }

  private Map<Long, Long> getProductCountsMap() {
    Map<Long, Long> counts = new HashMap<>();
    List<Object[]> rows = categoryRepository.countActiveProductsPerCategory();
    for (Object[] row : rows) {
      Long catId = (Long) row[0];
      Long count = (Long) row[1];
      if (catId != null) {
        counts.put(catId, count);
      }
    }
    return counts;
  }
}
