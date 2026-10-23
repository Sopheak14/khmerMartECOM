package com.khmermart.service.impl;

import com.khmermart.common.exception.CustomException;
import com.khmermart.common.exception.ErrorCode;
import com.khmermart.model.entity.Category;
import com.khmermart.model.response.CategoryDto;
import com.khmermart.repository.CategoryRepository;
import com.khmermart.service.CategoryService;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class CategoryServiceImpl implements CategoryService {

  private final CategoryRepository categoryRepository;

  public CategoryServiceImpl(CategoryRepository categoryRepository) {
    this.categoryRepository = categoryRepository;
  }

  @Override
  @Cacheable(value = "categories", key = "'tree'")
  public List<CategoryDto> getCategoryTree() {
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

    List<CategoryDto> result = new ArrayList<>();
    for (Category root : rootCategories) {
      result.add(buildDTOWithChildren(root, childrenMap, productCounts));
    }
    return result;
  }

  @Override
  public CategoryDto getCategoryBySlug(String slug) {
    Category category =
        categoryRepository
            .findBySlug(slug)
            .orElseThrow(() -> new CustomException(ErrorCode.NOT_FOUND, "Category not found"));

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

  @Override
  public CategoryDto getCategoryById(Long id) {
    if (id == null) return null;
    Category category = categoryRepository.findById(id).orElse(null);
    if (category == null) return null;
    return new CategoryDto(
        category.getId(),
        category.getName(),
        category.getNameKm(),
        category.getSlug(),
        category.getImageUrl(),
        category.getParentId(),
        List.of(),
        0);
  }

  private CategoryDto buildDTOWithChildren(
      Category category, Map<Long, List<Category>> childrenMap, Map<Long, Long> productCounts) {
    List<Category> children = childrenMap.getOrDefault(category.getId(), List.of());
    List<CategoryDto> childDTOs = new ArrayList<>();
    for (Category child : children) {
      childDTOs.add(buildDTOWithChildren(child, childrenMap, productCounts));
    }
    long count = productCounts.getOrDefault(category.getId(), 0L);
    return new CategoryDto(
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
