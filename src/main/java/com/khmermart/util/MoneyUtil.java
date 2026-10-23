package com.khmermart.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class MoneyUtil {

  private MoneyUtil() {}

  public static long toKhr(long amountCents, long usdKhrRate) {
    BigDecimal dollars =
        BigDecimal.valueOf(amountCents).divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);
    return dollars
        .multiply(BigDecimal.valueOf(usdKhrRate))
        .setScale(0, RoundingMode.HALF_UP)
        .longValue();
  }

  public static long percentOf(long amountCents, int percent) {
    return BigDecimal.valueOf(amountCents)
        .multiply(BigDecimal.valueOf(percent))
        .divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP)
        .longValue();
  }

  public static String formatUsd(long cents) {
    return String.format("$%.2f", cents / 100.0);
  }

  public static String formatKhr(long khr) {
    return String.format("KHR %,d", khr);
  }
}
