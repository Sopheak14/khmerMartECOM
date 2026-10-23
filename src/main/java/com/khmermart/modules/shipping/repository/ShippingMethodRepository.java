package com.khmermart.modules.shipping.repository;

import com.khmermart.modules.shipping.entity.ShippingMethod;
import com.khmermart.modules.shipping.model.ShippingMethodCode;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ShippingMethodRepository extends JpaRepository<ShippingMethod, Long> {
  Optional<ShippingMethod> findByCode(ShippingMethodCode code);
}
