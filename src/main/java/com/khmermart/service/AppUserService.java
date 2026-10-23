package com.khmermart.service;

import com.khmermart.model.request.AppUserUpdateRequest;
import com.khmermart.model.request.PasswordRequest;
import com.khmermart.model.response.AppUserDto;

public interface AppUserService {

  AppUserDto getProfile(Long userId);

  AppUserDto updateProfile(Long userId, AppUserUpdateRequest request);

  void changePassword(Long userId, PasswordRequest request);
}
