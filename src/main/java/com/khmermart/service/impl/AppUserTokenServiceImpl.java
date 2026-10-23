package com.khmermart.service.impl;

import com.khmermart.repository.AppUserTokenRepository;
import com.khmermart.service.AppUserTokenService;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AppUserTokenServiceImpl implements AppUserTokenService {

  private final AppUserTokenRepository appUserTokenRepository;

  public AppUserTokenServiceImpl(AppUserTokenRepository appUserTokenRepository) {
    this.appUserTokenRepository = appUserTokenRepository;
  }

  @Override
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void revokeFamilyImmediately(String familyId) {
    appUserTokenRepository.revokeFamily(familyId, Instant.now());
  }
}
