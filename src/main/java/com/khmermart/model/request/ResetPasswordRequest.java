package com.khmermart.model.request;

import com.khmermart.common.constant.MessageConstant;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ResetPasswordRequest(
    @NotBlank(message = "Token is required") String token,
    @NotBlank(message = "New password is required")
        @Pattern(
            regexp = "^(?=.*[A-Za-z])(?=.*\\d).{8,}$",
            message = MessageConstant.PASSWORD_INVALID)
        String newPassword) {}
