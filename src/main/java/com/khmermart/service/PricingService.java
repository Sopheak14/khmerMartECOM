package com.khmermart.service;

import com.khmermart.model.entity.Coupon;
import com.khmermart.model.entity.Promotion;
import com.khmermart.model.entity.ShippingMethod;
import java.util.List;

public interface PricingService {
  PricingResult calculate(
      List<PricingItem> items,
      List<Promotion> promotions,
      Coupon coupon,
      ShippingMethod shippingMethod);
}
