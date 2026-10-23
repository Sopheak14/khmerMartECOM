package com.khmermart.modules.auth.service;

import com.khmermart.modules.user.repository.RefreshTokenRepository;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RefreshTokenService {

  private final RefreshTokenRepository refreshTokenRepository;

  public RefreshTokenService(RefreshTokenRepository refreshTokenRepository) {
    this.refreshTokenRepository = refreshTokenRepository;
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void revokeFamilyImmediately(String familyId) {
    refreshTokenRepository.revokeFamily(familyId, Instant.now());
  }
}
