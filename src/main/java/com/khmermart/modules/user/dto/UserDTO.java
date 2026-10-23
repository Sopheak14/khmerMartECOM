package com.khmermart.modules.user.dto;

import com.khmermart.modules.user.model.Role;
import java.time.Instant;

public record UserDTO(
    Long id,
    String email,
    String fullName,
    String phone,
    Role role,
    boolean isActive,
    boolean emailVerified,
    Instant createdAt) {}
