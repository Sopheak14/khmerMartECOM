package com.khmermart.model.request;

import jakarta.validation.constraints.NotBlank;

public record CategoryRequest(
    @NotBlank(message = "Category name is required") String name,
    String nameKm,
    String slug,
    Long parentId,
    String imageUrl,
    Integer sortOrder) {}
