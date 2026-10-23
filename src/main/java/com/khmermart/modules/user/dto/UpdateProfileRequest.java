package com.khmermart.modules.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record UpdateProfileRequest(
    @NotBlank(message = "Full name is required") String fullName,
    @NotBlank(message = "Phone number is required")
        @Pattern(
            regexp = "^\\+855\\d{8,9}$",
            message = "Phone number must start with +855 followed by 8 or 9 digits")
        String phone) {}
