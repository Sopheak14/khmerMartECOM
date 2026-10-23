package com.khmermart.model.response;

import com.khmermart.model.enumerate.Roles;
import java.time.Instant;

public record AppUserDto(
    Long id,
    String email,
    String fullName,
    String phone,
    Roles role,
    boolean isActive,
    boolean emailVerified,
    Instant createdAt) {}
