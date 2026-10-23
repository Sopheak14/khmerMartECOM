package com.khmermart.modules.search.dto;

import java.util.List;

public record SearchSuggestDTO(
    List<ProductSuggestItem> products, List<CategorySuggestItem> categories) {

  public record ProductSuggestItem(
      String slug, String name, String imageUrl, Long priceCents) {}

  public record CategorySuggestItem(String slug, String name) {}
}
