package com.khmermart.modules.auth.dto;

import com.khmermart.modules.user.dto.UserDTO;

public record AuthResponse(String accessToken, UserDTO user) {}
