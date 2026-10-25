package com.khmermart.model.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record VariantDto(
    Long id,
    String sku,
    Map<String, String> attributes,
    Long priceCents,
    int available,
    Long salePriceCents) {

  public VariantDto(
      Long id, String sku, Map<String, String> attributes, Long priceCents, int available) {
    this(id, sku, attributes, priceCents, available, null);
  }
}
