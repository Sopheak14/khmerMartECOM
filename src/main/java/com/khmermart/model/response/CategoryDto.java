package com.khmermart.model.response;

import java.util.List;

public record CategoryDto(
    Long id,
    String name,
    String nameKm,
    String slug,
    String imageUrl,
    Long parentId,
    List<CategoryDto> children,
    long productCount) {}
