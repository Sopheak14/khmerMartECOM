package com.khmermart.modules.category.dto;

import java.util.List;

public record CategoryDTO(
    Long id,
    String name,
    String nameKm,
    String slug,
    String imageUrl,
    Long parentId,
    List<CategoryDTO> children,
    long productCount) {}
