package com.khmermart.common.money;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class Money {

  private Money() {}

  public static long toKhr(long cents, int khrRate) {
    return Math.round((double) cents / 100.0 * khrRate);
  }

  public static long percentOf(long cents, int percent) {
    return Math.round((double) cents * percent / 100.0);
  }

  public static String formatUsd(long cents) {
    return String.format("$%.2f", cents / 100.0);
  }

  public static double toUsdAmount(long cents) {
    return BigDecimal.valueOf(cents)
        .movePointLeft(2)
        .setScale(2, RoundingMode.HALF_UP)
        .doubleValue();
  }
}
