package com.khmermart.service;

import com.khmermart.model.request.AdminCouponRequest;
import com.khmermart.model.request.AdminPromotionRequest;
import com.khmermart.model.response.AdminCouponDto;
import com.khmermart.model.response.AdminPromotionDto;
import java.util.List;

public interface AdminMarketingService {

  List<AdminCouponDto> getCoupons();

  AdminCouponDto getCoupon(Long id);

  AdminCouponDto createCoupon(AdminCouponRequest request, Long adminId, String ip);

  AdminCouponDto updateCoupon(Long id, AdminCouponRequest request, Long adminId, String ip);

  void deleteCoupon(Long id, Long adminId, String ip);

  List<AdminPromotionDto> getPromotions();

  AdminPromotionDto getPromotion(Long id);

  AdminPromotionDto createPromotion(AdminPromotionRequest request, Long adminId, String ip);

  AdminPromotionDto updatePromotion(
      Long id, AdminPromotionRequest request, Long adminId, String ip);

  void deletePromotion(Long id, Long adminId, String ip);
}
