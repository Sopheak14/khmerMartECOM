package com.khmermart.repository;

import com.khmermart.model.entity.Promotion;
import com.khmermart.model.enumerate.PromotionType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface PromotionRepository extends JpaRepository<Promotion, Long> {
  List<Promotion> findByTypeAndActiveTrue(PromotionType type);

  @Query(
      """
            SELECT p FROM Promotion p
            WHERE p.active = true AND p.startsAt <= :now AND p.endsAt > :now
            """)
  List<Promotion> findActivePromotions(@Param("now") Instant now);

  @Query(
      """
            SELECT p FROM Promotion p
            WHERE p.type = :type AND p.active = true AND p.startsAt <= :now AND p.endsAt > :now
            """)
  Optional<Promotion> findFirstActiveByType(
      @Param("type") PromotionType type, @Param("now") Instant now);
}
