package com.khmermart.repository;

import com.khmermart.model.entity.AppUserToken;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface AppUserTokenRepository extends JpaRepository<AppUserToken, Long> {
  Optional<AppUserToken> findByTokenHash(String tokenHash);

  List<AppUserToken> findByFamilyId(String familyId);

  @Modifying
  @Query("UPDATE AppUserToken t SET t.revokedAt = :now WHERE t.familyId = :familyId")
  void revokeFamily(@Param("familyId") String familyId, @Param("now") Instant now);

  void deleteByUserId(Long userId);
}
