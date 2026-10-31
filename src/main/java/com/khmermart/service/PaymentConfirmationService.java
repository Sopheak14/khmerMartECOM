package com.khmermart.service;

import com.khmermart.model.entity.Payment;
import com.khmermart.model.response.BakongTxData;

public interface PaymentConfirmationService {
  Payment confirm(Long paymentId, BakongTxData txData);

  /**
   * Records a payment that Bakong confirmed AFTER the payment/order had already expired. The money
   * is real, so the payment becomes PAID (hash, accounts, externalRef stored) and the order is
   * flagged for admin attention. Stock was already released at expiry, so inventory is not touched
   * and the order status is left for an admin to resolve.
   */
  Payment confirmLate(Long paymentId, BakongTxData txData);

  Payment confirm(
      Long paymentId, String hash, String fromAccountId, String toAccountId, String rawResponse);
}
