package com.khmermart.service;

import com.khmermart.model.enumerate.Roles;
import com.khmermart.model.request.AdminUserUpdateRequest;
import com.khmermart.model.response.AppUserDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface AdminUserService {

  Page<AppUserDto> getUsers(String query, Roles role, Pageable pageable);

  AppUserDto updateUser(Long id, AdminUserUpdateRequest request, Long adminId, String ip);
}
