package com.khmermart.service;

import com.khmermart.model.response.KhqrPaymentDto;
import com.khmermart.model.response.PaymentStatusDto;

public interface KhqrService {
  KhqrPaymentDto generateKhqr(Long userId, String orderNumber, String currencyOverride);

  KhqrPaymentDto regenerateKhqr(Long userId, String orderNumber);

  PaymentStatusDto getPaymentStatus(Long userId, Long paymentId, boolean isStaffOrAdmin);

  PaymentStatusDto simulatePaid(Long userId, Long paymentId, boolean isStaffOrAdmin);
}
