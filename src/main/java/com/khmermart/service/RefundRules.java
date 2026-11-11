package com.khmermart.service;

import com.khmermart.model.entity.Payment;
import com.khmermart.model.enumerate.PaidMode;
import com.khmermart.model.enumerate.PaymentMethod;
import java.util.Locale;

/** Small helpers shared by the cancel / refund flows. */
public final class RefundRules {

  private RefundRules() {}

  /** Mode for a payment confirmed with this transaction hash: MOCK-… hashes are simulated. */
  public static PaidMode modeForHash(String hash) {
    return hash != null && hash.startsWith("MOCK-") ? PaidMode.MOCK : PaidMode.LIVE;
  }

  /** Stored mode, or a best-effort derivation for rows that predate / bypass the column. */
  public static PaidMode effectiveMode(Payment p) {
    if (p.getPaidMode() != null) {
      return p.getPaidMode();
    }
    if (p.getBakongHash() != null && p.getBakongHash().startsWith("MOCK-")) {
      return PaidMode.MOCK;
    }
    if (p.getMethod() == PaymentMethod.COD) {
      return PaidMode.COD;
    }
    if (p.getMethod() == PaymentMethod.KHQR || p.getMethod() == PaymentMethod.BANK_TRANSFER) {
      return PaidMode.LIVE;
    }
    return PaidMode.MOCK; // CARD / WALLET have no real gateway in this app
  }

  public static String usd(long cents) {
    return "$" + String.format(Locale.US, "%.2f", cents / 100.0);
  }
}
