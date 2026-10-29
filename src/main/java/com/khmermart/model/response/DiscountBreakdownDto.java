package com.khmermart.model.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;

/**
 * The discount split of an order, read from the checkout snapshot (orders.discount_breakdown),
 * never recalculated. Orders without a snapshot give an empty promotions list and a null coupon.
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record DiscountBreakdownDto(List<PromotionLine> promotions, CouponLine coupon) {

  public record PromotionLine(String label, Long amountCents) {}

  public record CouponLine(String code, Long amountCents) {}

  public static DiscountBreakdownDto empty() {
    return new DiscountBreakdownDto(List.of(), null);
  }

  public static DiscountBreakdownDto fromSnapshot(String json, ObjectMapper objectMapper) {
    if (json == null || json.isBlank()) {
      return empty();
    }
    try {
      JsonNode root = objectMapper.readTree(json);
      List<PromotionLine> promotions = new ArrayList<>();
      for (JsonNode p : root.path("promotions")) {
        promotions.add(
            new PromotionLine(p.path("label").asText(""), p.path("amountCents").asLong()));
      }
      JsonNode c = root.path("coupon");
      CouponLine coupon =
          c.isObject()
              ? new CouponLine(c.path("code").asText(""), c.path("amountCents").asLong())
              : null;
      return new DiscountBreakdownDto(promotions, coupon);
    } catch (Exception ex) {
      return empty();
    }
  }
}
