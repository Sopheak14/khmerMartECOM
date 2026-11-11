package com.khmermart.model.enumerate;

/** How a payment was actually settled. Decides whether a refund moves real money. */
public enum PaidMode {
  /** Simulated payment (KHQR MOCK mode / seed data): no real money moved. */
  MOCK,
  /** Real money received through Bakong / bank transfer. */
  LIVE,
  /** Cash collected on delivery. */
  COD
}
