package com.khmermart.repository;

import com.khmermart.model.entity.Address;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface AddressRepository extends JpaRepository<Address, Long> {
  List<Address> findByUserIdOrderByIdAsc(Long userId);

  long countByUserId(Long userId);

  Optional<Address> findByIdAndUserId(Long id, Long userId);

  @Modifying
  @Query("UPDATE Address a SET a.isDefault = false WHERE a.userId = :userId")
  void clearDefaultForUser(@Param("userId") Long userId);
}
