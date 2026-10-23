package com.khmermart.modules.user.repository;

import com.khmermart.modules.user.entity.Wishlist;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface WishlistRepository extends JpaRepository<Wishlist, Wishlist.WishlistId> {
  List<Wishlist> findByUserId(Long userId);

  void deleteByUserIdAndProductId(Long userId, Long productId);

  boolean existsByUserIdAndProductId(Long userId, Long productId);
}
