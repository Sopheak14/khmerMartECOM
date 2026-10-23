package com.khmermart.controller;

import com.khmermart.common.response.SuccessResponse;
import com.khmermart.model.response.CategoryDto;
import com.khmermart.service.CategoryService;
import com.khmermart.util.ApiResponseUtil;
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
  public SuccessResponse<List<CategoryDto>> getCategories() {
    return ApiResponseUtil.success(categoryService.getCategoryTree());
  }

  @GetMapping("/{slug}")
  @Operation(summary = "Get category by slug with children")
  public SuccessResponse<CategoryDto> getCategoryBySlug(@PathVariable String slug) {
    return ApiResponseUtil.success(categoryService.getCategoryBySlug(slug));
  }
}
