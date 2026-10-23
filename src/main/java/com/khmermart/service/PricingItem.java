package com.khmermart.service;

public record PricingItem(
    Long productId,
    Long variantId,
    Long unitPriceCents,
    int quantity,
    Long flashSalePriceCents,
    boolean bogoEligible,
    Integer seasonalPercent) {

  public PricingItem(
      Long productId, Long variantId, Long unitPriceCents, int quantity, Long flashSalePriceCents) {
    this(productId, variantId, unitPriceCents, quantity, flashSalePriceCents, false, null);
  }

  public PricingItem(
      Long productId,
      Long variantId,
      Long unitPriceCents,
      int quantity,
      Long flashSalePriceCents,
      boolean bogoEligible) {
    this(productId, variantId, unitPriceCents, quantity, flashSalePriceCents, bogoEligible, null);
  }
}
