package com.khmermart.modules.address.repository;

import com.khmermart.modules.address.entity.Address;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AddressRepository extends JpaRepository<Address, Long> {
  List<Address> findByUserId(Long userId);

  Optional<Address> findByIdAndUserId(Long id, Long userId);

  Optional<Address> findByUserIdAndIsDefaultTrue(Long userId);
}
