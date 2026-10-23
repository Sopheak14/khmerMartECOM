package com.khmermart.modules.user.service;

import com.khmermart.common.api.ApiException;
import com.khmermart.common.api.ErrorCode;
import com.khmermart.modules.user.dto.ChangePasswordRequest;
import com.khmermart.modules.user.dto.UpdateProfileRequest;
import com.khmermart.modules.user.dto.UserDTO;
import com.khmermart.modules.user.entity.User;
import com.khmermart.modules.user.mapper.UserMapper;
import com.khmermart.modules.user.repository.UserRepository;
import java.time.Instant;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class UserService {

  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;
  private final UserMapper userMapper;

  public UserService(
      UserRepository userRepository, PasswordEncoder passwordEncoder, UserMapper userMapper) {
    this.userRepository = userRepository;
    this.passwordEncoder = passwordEncoder;
    this.userMapper = userMapper;
  }

  @Transactional(readOnly = true)
  public UserDTO getProfile(Long userId) {
    User user =
        userRepository
            .findById(userId)
            .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "User not found"));
    return userMapper.toDTO(user);
  }

  public UserDTO updateProfile(Long userId, UpdateProfileRequest request) {
    User user =
        userRepository
            .findById(userId)
            .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "User not found"));

    user.setFullName(request.fullName().trim());
    user.setPhone(request.phone().trim());
    user.setUpdatedAt(Instant.now());

    User saved = userRepository.save(user);
    return userMapper.toDTO(saved);
  }

  public void changePassword(Long userId, ChangePasswordRequest request) {
    User user =
        userRepository
            .findById(userId)
            .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "User not found"));

    if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
      throw new ApiException(
          ErrorCode.VALIDATION_ERROR,
          "Validation failed",
          java.util.Map.of("currentPassword", "Current password is incorrect"));
    }

    user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
    user.setUpdatedAt(Instant.now());
    userRepository.save(user);
  }
}
