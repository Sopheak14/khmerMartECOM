package com.khmermart.modules.coupon.repository;

import com.khmermart.modules.coupon.entity.CouponRedemption;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CouponRedemptionRepository extends JpaRepository<CouponRedemption, Long> {
  int countByCouponIdAndUserId(Long couponId, Long userId);

  List<CouponRedemption> findByOrderId(Long orderId);

  Optional<CouponRedemption> findByCouponIdAndOrderId(Long couponId, Long orderId);
}
