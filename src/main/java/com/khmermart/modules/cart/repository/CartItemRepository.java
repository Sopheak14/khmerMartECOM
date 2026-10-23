package com.khmermart.modules.cart.repository;

import com.khmermart.modules.cart.entity.CartItem;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CartItemRepository extends JpaRepository<CartItem, Long> {
  List<CartItem> findByCartId(Long cartId);

  Optional<CartItem> findByCartIdAndProductIdAndVariantId(
      Long cartId, Long productId, Long variantId);

  Optional<CartItem> findByCartIdAndProductIdAndVariantIdIsNull(Long cartId, Long productId);

  void deleteByCartId(Long cartId);
}
