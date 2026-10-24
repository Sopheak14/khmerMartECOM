package com.khmermart.service;

import com.khmermart.model.request.CategoryRequest;
import com.khmermart.model.response.CategoryDto;
import java.util.List;

public interface AdminCategoryService {

  List<CategoryDto> getAllCategories();

  CategoryDto createCategory(CategoryRequest request, Long adminId, String ip);

  CategoryDto updateCategory(Long id, CategoryRequest request, Long adminId, String ip);

  void deleteCategory(Long id, Long adminId, String ip);
}
