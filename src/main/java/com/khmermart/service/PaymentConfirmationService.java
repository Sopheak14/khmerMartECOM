package com.khmermart.service;

import com.khmermart.model.entity.Payment;
import com.khmermart.model.response.BakongTxData;

public interface PaymentConfirmationService {
  Payment confirm(Long paymentId, BakongTxData txData);

  Payment confirm(
      Long paymentId, String hash, String fromAccountId, String toAccountId, String rawResponse);
}
