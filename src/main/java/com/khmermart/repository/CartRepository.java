package com.khmermart.repository;

import com.khmermart.model.entity.Cart;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CartRepository extends JpaRepository<Cart, Long> {
  Optional<Cart> findByUserId(Long userId);

  Optional<Cart> findByGuestToken(String guestToken);
}
