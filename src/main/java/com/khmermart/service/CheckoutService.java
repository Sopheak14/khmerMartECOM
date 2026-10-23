package com.khmermart.service;

import com.khmermart.model.request.CheckoutRequest;
import com.khmermart.model.response.CheckoutPreviewDto;
import com.khmermart.model.response.CheckoutResponseDto;

public interface CheckoutService {

  CheckoutPreviewDto preview(Long userId, CheckoutRequest request);

  CheckoutResponseDto checkout(Long userId, String idempotencyKey, CheckoutRequest request);
}
