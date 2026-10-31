package com.khmermart.service;

import com.khmermart.model.response.PaymentCheckResultDto;

/**
 * The single place that asks Bakong about a KHQR payment and applies the result. Used by the
 * background poller, the expiry job, the customer status endpoint and the admin re-check button so
 * they all behave (and log) identically.
 */
public interface KhqrPaymentChecker {

  /**
   * Checks the payment against Bakong once. Always bumps check_count / last_checked_at, logs the
   * outcome, and on success marks the payment PAID (or records it as a late payment).
   *
   * @param trigger who asked, for the log line (POLLER, EXPIRY_JOB, STATUS_POLL, ADMIN_RECHECK)
   */
  PaymentCheckResultDto check(Long paymentId, String trigger);
}
