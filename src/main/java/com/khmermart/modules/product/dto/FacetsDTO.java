package com.khmermart.modules.product.dto;

import java.util.List;

public record FacetsDTO(
    List<FacetValueDTO> brands,
    List<AttributeFacetDTO> attributes,
    PriceRangeDTO priceRange) {

  public record FacetValueDTO(String value, long count) {}

  public record AttributeFacetDTO(String name, List<FacetValueDTO> values) {}

  public record PriceRangeDTO(Long minCents, Long maxCents) {}
}
