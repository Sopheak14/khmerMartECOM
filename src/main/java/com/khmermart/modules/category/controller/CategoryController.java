package com.khmermart.modules.category.controller;

import com.khmermart.common.api.ApiResponse;
import com.khmermart.modules.category.dto.CategoryDTO;
import com.khmermart.modules.category.service.CategoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/categories")
@Tag(name = "Category", description = "Product category tree and navigation")
public class CategoryController {

  private final CategoryService categoryService;

  public CategoryController(CategoryService categoryService) {
    this.categoryService = categoryService;
  }

  @GetMapping
  @Operation(summary = "Get full category tree")
  public ApiResponse<List<CategoryDTO>> getCategories() {
    return ApiResponse.ok(categoryService.getCategoryTree());
  }

  @GetMapping("/{slug}")
  @Operation(summary = "Get category by slug with children")
  public ApiResponse<CategoryDTO> getCategoryBySlug(@PathVariable String slug) {
    return ApiResponse.ok(categoryService.getCategoryBySlug(slug));
  }
}
