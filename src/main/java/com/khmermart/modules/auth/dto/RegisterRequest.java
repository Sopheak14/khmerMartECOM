package com.khmermart.modules.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record RegisterRequest(
    @NotBlank(message = "Email is required") @Email(message = "Invalid email format") String email,
    @NotBlank(message = "Password is required")
        @Pattern(
            regexp = "^(?=.*[A-Za-z])(?=.*\\d).{8,}$",
            message = "Password must be at least 8 characters and contain both letters and numbers")
        String password,
    @NotBlank(message = "Full name is required") String fullName,
    @NotBlank(message = "Phone number is required")
        @Pattern(
            regexp = "^\\+855\\d{8,9}$",
            message = "Phone number must start with +855 followed by 8 or 9 digits")
        String phone) {}
