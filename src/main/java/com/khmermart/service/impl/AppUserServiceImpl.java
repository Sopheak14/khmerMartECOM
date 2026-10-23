package com.khmermart.service.impl;

import com.khmermart.common.constant.MessageConstant;
import com.khmermart.common.exception.CustomException;
import com.khmermart.common.exception.ErrorCode;
import com.khmermart.mapper.AppUserMapper;
import com.khmermart.model.entity.AppUser;
import com.khmermart.model.request.AppUserUpdateRequest;
import com.khmermart.model.request.PasswordRequest;
import com.khmermart.model.response.AppUserDto;
import com.khmermart.repository.AppUserRepository;
import com.khmermart.service.AppUserService;
import java.time.Instant;
import java.util.Map;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class AppUserServiceImpl implements AppUserService {

  private final AppUserRepository appUserRepository;
  private final PasswordEncoder passwordEncoder;
  private final AppUserMapper appUserMapper;

  public AppUserServiceImpl(
      AppUserRepository appUserRepository,
      PasswordEncoder passwordEncoder,
      AppUserMapper appUserMapper) {
    this.appUserRepository = appUserRepository;
    this.passwordEncoder = passwordEncoder;
    this.appUserMapper = appUserMapper;
  }

  @Override
  @Transactional(readOnly = true)
  public AppUserDto getProfile(Long userId) {
    AppUser user =
        appUserRepository
            .findById(userId)
            .orElseThrow(
                () -> new CustomException(ErrorCode.NOT_FOUND, MessageConstant.USER_NOT_FOUND));
    return appUserMapper.toDTO(user);
  }

  @Override
  public AppUserDto updateProfile(Long userId, AppUserUpdateRequest request) {
    AppUser user =
        appUserRepository
            .findById(userId)
            .orElseThrow(
                () -> new CustomException(ErrorCode.NOT_FOUND, MessageConstant.USER_NOT_FOUND));

    user.setFullName(request.fullName().trim());
    user.setPhone(request.phone().trim());
    user.setUpdatedAt(Instant.now());

    AppUser saved = appUserRepository.save(user);
    return appUserMapper.toDTO(saved);
  }

  @Override
  public void changePassword(Long userId, PasswordRequest request) {
    AppUser user =
        appUserRepository
            .findById(userId)
            .orElseThrow(
                () -> new CustomException(ErrorCode.NOT_FOUND, MessageConstant.USER_NOT_FOUND));

    if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
      throw new CustomException(
          ErrorCode.VALIDATION_ERROR,
          MessageConstant.VALIDATION_FAILED,
          Map.of("currentPassword", MessageConstant.CURRENT_PASSWORD_INCORRECT));
    }

    user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
    user.setUpdatedAt(Instant.now());
    appUserRepository.save(user);
  }
}
