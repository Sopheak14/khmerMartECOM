package com.khmermart.repository;

import com.khmermart.model.entity.CouponRedemption;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CouponRedemptionRepository extends JpaRepository<CouponRedemption, Long> {
  int countByCouponIdAndUserId(Long couponId, Long userId);

  Optional<CouponRedemption> findByOrderId(Long orderId);

  Optional<CouponRedemption> findByCouponIdAndOrderId(Long couponId, Long orderId);
}
