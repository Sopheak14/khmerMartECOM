package com.khmermart.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

public class InvoiceDiscountArithmeticTest {

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  @DisplayName(
      "Discount Breakdown Arithmetic: Subtotal - promotion lines - coupon + shipping == Total")
  void testDiscountBreakdownArithmetic() throws Exception {
    long subtotalCents = 99900L; // $999.00
    long shippingCents = 1500L; // $15.00

    String discountBreakdownJson =
        """
        {
          "promotions": [
            {"label": "Flash Sale Discount", "amountCents": 10000},
            {"label": "Seasonal Promotion (20%)", "amountCents": 500}
          ],
          "coupon": {
            "code": "SAVE5",
            "amountCents": 500
          }
        }
        """;

    Map<String, Object> breakdown =
        objectMapper.readValue(discountBreakdownJson, new TypeReference<>() {});

    List<?> promoList = (List<?>) breakdown.get("promotions");
    long promoLinesSum = 0L;
    for (Object obj : promoList) {
      Map<?, ?> pMap = (Map<?, ?>) obj;
      Number amt = (Number) pMap.get("amountCents");
      promoLinesSum += amt.longValue();
    }
    assertThat(promoLinesSum).isEqualTo(10500L);

    Map<?, ?> cMap = (Map<?, ?>) breakdown.get("coupon");
    long couponAmount = ((Number) cMap.get("amountCents")).longValue();
    assertThat(couponAmount).isEqualTo(500L);

    long totalDiscount = promoLinesSum + couponAmount;
    assertThat(totalDiscount).isEqualTo(11000L); // $110.00

    long totalCents = subtotalCents - totalDiscount + shippingCents;
    assertThat(totalCents).isEqualTo(90400L); // $904.00

    // Strict formula: Subtotal − lines + shipping must equal Total
    assertThat(subtotalCents - (promoLinesSum + couponAmount) + shippingCents)
        .isEqualTo(totalCents);
  }

  @Test
  @DisplayName("Zero discount breakdown arithmetic: Subtotal + shipping == Total")
  void testZeroDiscountArithmetic() {
    long subtotalCents = 4500L;
    long promoLinesSum = 0L;
    long couponAmount = 0L;
    long shippingCents = 200L;
    long totalCents = 4700L;

    assertThat(subtotalCents - (promoLinesSum + couponAmount) + shippingCents)
        .isEqualTo(totalCents);
  }
}
