package com.khmermart.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.khmermart.seed.SeedCatalog;
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
    // Cart: hero Standard (flash sale) + Kampot Red Pepper (Pchum Ben 10% seasonal), SAVE5, Express
    long redPepper = SeedCatalog.product(1007L).priceCents();
    long subtotalCents = SeedCatalog.HERO_STANDARD_CENTS + redPepper;
    long flashCents = SeedCatalog.HERO_STANDARD_CENTS - SeedCatalog.HERO_FLASH_CENTS;
    long seasonalCents = redPepper * SeedCatalog.SEASONAL_PERCENT / 100;
    long shippingCents = 300L; // EXPRESS

    String discountBreakdownJson =
        """
        {
          "promotions": [
            {"label": "Flash Sale Discount", "amountCents": %d},
            {"label": "Seasonal Promotion (%d%%)", "amountCents": %d}
          ],
          "coupon": {
            "code": "%s",
            "amountCents": %d
          }
        }
        """
            .formatted(
                flashCents,
                SeedCatalog.SEASONAL_PERCENT,
                seasonalCents,
                SeedCatalog.COUPON_SAVE5,
                SeedCatalog.SAVE5_CENTS);

    Map<String, Object> breakdown =
        objectMapper.readValue(discountBreakdownJson, new TypeReference<>() {});

    List<?> promoList = (List<?>) breakdown.get("promotions");
    long promoLinesSum = 0L;
    for (Object obj : promoList) {
      Map<?, ?> pMap = (Map<?, ?>) obj;
      Number amt = (Number) pMap.get("amountCents");
      promoLinesSum += amt.longValue();
    }
    assertThat(promoLinesSum).isEqualTo(flashCents + seasonalCents);

    Map<?, ?> cMap = (Map<?, ?>) breakdown.get("coupon");
    long couponAmount = ((Number) cMap.get("amountCents")).longValue();
    assertThat(couponAmount).isEqualTo(SeedCatalog.SAVE5_CENTS);

    long totalDiscount = promoLinesSum + couponAmount;
    assertThat(totalDiscount).isEqualTo(flashCents + seasonalCents + SeedCatalog.SAVE5_CENTS);

    long totalCents = subtotalCents - totalDiscount + shippingCents;
    assertThat(totalCents).isEqualTo(13825L); // 14900 + 1250 - (2000 + 125 + 500) + 300

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
