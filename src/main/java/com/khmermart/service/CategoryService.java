package com.khmermart.service;

import com.khmermart.model.response.CategoryDto;
import java.util.List;

public interface CategoryService {

  List<CategoryDto> getCategoryTree();

  CategoryDto getCategoryBySlug(String slug);

  CategoryDto getCategoryById(Long id);
}
