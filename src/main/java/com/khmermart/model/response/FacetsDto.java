package com.khmermart.model.response;

import java.util.List;

public record FacetsDto(
    List<FacetValueDto> brands, List<AttributeFacetDto> attributes, PriceRangeDto priceRange) {

  public record FacetValueDto(String value, long count) {}

  public record AttributeFacetDto(String name, List<FacetValueDto> values) {}

  public record PriceRangeDto(Long minCents, Long maxCents) {}
}
