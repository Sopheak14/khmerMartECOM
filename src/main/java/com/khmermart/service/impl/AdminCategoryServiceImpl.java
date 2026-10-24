package com.khmermart.service.impl;

import com.khmermart.common.exception.CustomException;
import com.khmermart.common.exception.ErrorCode;
import com.khmermart.model.entity.Category;
import com.khmermart.model.request.CategoryRequest;
import com.khmermart.model.response.CategoryDto;
import com.khmermart.repository.CategoryRepository;
import com.khmermart.repository.ProductRepository;
import com.khmermart.service.AdminCategoryService;
import com.khmermart.service.AuditLogService;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminCategoryServiceImpl implements AdminCategoryService {

  private final CategoryRepository categoryRepository;
  private final ProductRepository productRepository;
  private final AuditLogService auditLogService;

  public AdminCategoryServiceImpl(
      CategoryRepository categoryRepository,
      ProductRepository productRepository,
      AuditLogService auditLogService) {
    this.categoryRepository = categoryRepository;
    this.productRepository = productRepository;
    this.auditLogService = auditLogService;
  }

  @Override
  @Transactional(readOnly = true)
  public List<CategoryDto> getAllCategories() {
    return categoryRepository.findAllByOrderBySortOrderAsc().stream().map(this::toDto).toList();
  }

  @Override
  @Transactional
  @CacheEvict(
      value = {"categories", "home"},
      allEntries = true)
  public CategoryDto createCategory(CategoryRequest request, Long adminId, String ip) {
    String slug = request.slug();
    if (slug == null || slug.isBlank()) {
      slug = generateSlug(request.name());
    } else {
      slug = slug.trim().toLowerCase(Locale.ROOT);
    }

    if (categoryRepository.existsBySlug(slug)) {
      throw new CustomException(
          ErrorCode.CONFLICT, "Category with slug '" + slug + "' already exists");
    }

    Category category =
        Category.builder()
            .name(request.name().trim())
            .nameKm(request.nameKm())
            .slug(slug)
            .parentId(request.parentId())
            .imageUrl(request.imageUrl())
            .sortOrder(request.sortOrder() != null ? request.sortOrder() : 0)
            .build();

    Category saved = categoryRepository.save(category);

    auditLogService.log(
        adminId, "CREATE_CATEGORY", "CATEGORY", String.valueOf(saved.getId()), null, saved, ip);

    return toDto(saved);
  }

  @Override
  @Transactional
  @CacheEvict(
      value = {"categories", "home"},
      allEntries = true)
  public CategoryDto updateCategory(Long id, CategoryRequest request, Long adminId, String ip) {
    Category category =
        categoryRepository
            .findById(id)
            .orElseThrow(() -> new CustomException(ErrorCode.NOT_FOUND, "Category not found"));

    Map<String, Object> beforeState =
        Map.of(
            "name", category.getName(),
            "slug", category.getSlug(),
            "parentId", category.getParentId() != null ? category.getParentId() : 0);

    if (request.name() != null && !request.name().isBlank()) {
      category.setName(request.name().trim());
    }
    if (request.nameKm() != null) {
      category.setNameKm(request.nameKm().trim());
    }
    if (request.slug() != null && !request.slug().isBlank()) {
      String newSlug = request.slug().trim().toLowerCase(Locale.ROOT);
      if (!newSlug.equals(category.getSlug()) && categoryRepository.existsBySlug(newSlug)) {
        throw new CustomException(
            ErrorCode.CONFLICT, "Category with slug '" + newSlug + "' already exists");
      }
      category.setSlug(newSlug);
    }
    if (request.parentId() != null) {
      if (request.parentId().equals(id)) {
        throw new CustomException(ErrorCode.VALIDATION_ERROR, "Category cannot be its own parent");
      }
      category.setParentId(request.parentId());
    }
    if (request.imageUrl() != null) {
      category.setImageUrl(request.imageUrl());
    }
    if (request.sortOrder() != null) {
      category.setSortOrder(request.sortOrder());
    }

    Category updated = categoryRepository.save(category);

    auditLogService.log(
        adminId,
        "UPDATE_CATEGORY",
        "CATEGORY",
        String.valueOf(updated.getId()),
        beforeState,
        updated,
        ip);

    return toDto(updated);
  }

  @Override
  @Transactional
  @CacheEvict(
      value = {"categories", "home"},
      allEntries = true)
  public void deleteCategory(Long id, Long adminId, String ip) {
    Category category =
        categoryRepository
            .findById(id)
            .orElseThrow(() -> new CustomException(ErrorCode.NOT_FOUND, "Category not found"));

    if (productRepository.existsByCategoryId(id)) {
      throw new CustomException(ErrorCode.CONFLICT, "Cannot delete category that has products");
    }

    if (!categoryRepository.findByParentId(id).isEmpty()) {
      throw new CustomException(
          ErrorCode.CONFLICT, "Cannot delete category that has subcategories");
    }

    categoryRepository.delete(category);

    auditLogService.log(
        adminId,
        "DELETE_CATEGORY",
        "CATEGORY",
        String.valueOf(id),
        Map.of("id", id, "name", category.getName()),
        Map.of("deleted", true),
        ip);
  }

  private CategoryDto toDto(Category c) {
    return new CategoryDto(
        c.getId(),
        c.getName(),
        c.getNameKm(),
        c.getSlug(),
        c.getImageUrl(),
        c.getParentId(),
        List.of(),
        0L);
  }

  private String generateSlug(String name) {
    String slug = name.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9\\s-]", "");
    return slug.replaceAll("\\s+", "-");
  }
}
