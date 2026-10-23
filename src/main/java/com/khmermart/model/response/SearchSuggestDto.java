package com.khmermart.model.response;

import java.util.List;

public record SearchSuggestDto(
    List<ProductSuggestItem> products, List<CategorySuggestItem> categories) {

  public record ProductSuggestItem(
      String slug, String name, String nameKm, String imageUrl, Long priceCents) {}

  public record CategorySuggestItem(String slug, String name, String nameKm) {}
}
