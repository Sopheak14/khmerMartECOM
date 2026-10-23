package com.khmermart.repository;

import com.khmermart.model.entity.ShippingMethod;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ShippingMethodRepository extends JpaRepository<ShippingMethod, Long> {
  Optional<ShippingMethod> findByCode(String code);

  java.util.List<ShippingMethod> findAllByOrderByFeeCentsAsc();
}
