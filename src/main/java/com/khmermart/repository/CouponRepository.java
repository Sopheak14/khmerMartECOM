package com.khmermart.repository;

import com.khmermart.model.entity.Coupon;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CouponRepository extends JpaRepository<Coupon, Long> {
  Optional<Coupon> findByCodeIgnoreCaseAndActiveTrue(String code);

  Optional<Coupon> findByCode(String code);

  boolean existsByCode(String code);
}
