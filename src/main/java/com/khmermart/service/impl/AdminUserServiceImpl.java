package com.khmermart.service.impl;

import com.khmermart.common.exception.CustomException;
import com.khmermart.common.exception.ErrorCode;
import com.khmermart.mapper.AppUserMapper;
import com.khmermart.model.entity.AppUser;
import com.khmermart.model.enumerate.Roles;
import com.khmermart.model.request.AdminUserUpdateRequest;
import com.khmermart.model.response.AppUserDto;
import com.khmermart.repository.AppUserRepository;
import com.khmermart.repository.AppUserTokenRepository;
import com.khmermart.service.AdminUserService;
import com.khmermart.service.AuditLogService;
import java.time.Instant;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminUserServiceImpl implements AdminUserService {

  private final AppUserRepository appUserRepository;
  private final AppUserTokenRepository appUserTokenRepository;
  private final AppUserMapper appUserMapper;
  private final AuditLogService auditLogService;

  public AdminUserServiceImpl(
      AppUserRepository appUserRepository,
      AppUserTokenRepository appUserTokenRepository,
      AppUserMapper appUserMapper,
      AuditLogService auditLogService) {
    this.appUserRepository = appUserRepository;
    this.appUserTokenRepository = appUserTokenRepository;
    this.appUserMapper = appUserMapper;
    this.auditLogService = auditLogService;
  }

  @Override
  @Transactional(readOnly = true)
  public Page<AppUserDto> getUsers(String query, Roles role, Pageable pageable) {
    Specification<AppUser> spec = Specification.where(null);

    if (query != null && !query.isBlank()) {
      String term = "%" + query.trim().toLowerCase() + "%";
      spec =
          spec.and(
              (root, q, cb) ->
                  cb.or(
                      cb.like(cb.lower(root.get("email")), term),
                      cb.like(cb.lower(root.get("fullName")), term),
                      cb.like(cb.lower(root.get("phone")), term)));
    }

    if (role != null) {
      spec = spec.and((root, q, cb) -> cb.equal(root.get("role"), role));
    }

    return appUserRepository.findAll(spec, pageable).map(appUserMapper::toDTO);
  }

  @Override
  @Transactional
  public AppUserDto updateUser(Long id, AdminUserUpdateRequest request, Long adminId, String ip) {
    AppUser user =
        appUserRepository
            .findById(id)
            .orElseThrow(() -> new CustomException(ErrorCode.NOT_FOUND, "User not found"));

    Map<String, Object> beforeState = Map.of("role", user.getRole(), "isActive", user.isActive());

    if (request.role() != null) {
      user.setRole(request.role());
    }

    if (request.isActive() != null) {
      user.setActive(request.isActive());
      if (!request.isActive()) {
        // Deactivated user: revoke all active refresh tokens immediately
        appUserTokenRepository.revokeAllByUserId(user.getId(), Instant.now());
      }
    }

    user.setUpdatedAt(Instant.now());
    AppUser updated = appUserRepository.save(user);

    Map<String, Object> afterState =
        Map.of("role", updated.getRole(), "isActive", updated.isActive());

    auditLogService.log(
        adminId, "UPDATE_USER", "USER", String.valueOf(user.getId()), beforeState, afterState, ip);

    return appUserMapper.toDTO(updated);
  }
}
