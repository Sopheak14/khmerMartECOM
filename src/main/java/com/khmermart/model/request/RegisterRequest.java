package com.khmermart.model.request;

import com.khmermart.common.constant.MessageConstant;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
    @NotBlank(message = "Email is required") @Email(message = "Invalid email format") String email,
    @NotBlank(message = "Password is required")
        @Pattern(
            regexp = "^(?=.*[A-Za-z])(?=.*\\d).{8,}$",
            message = MessageConstant.PASSWORD_INVALID)
        String password,
    @NotBlank(message = "Full name is required")
        @Size(max = 255, message = "Full name cannot exceed 255 characters")
        String fullName,
    @NotBlank(message = "Phone number is required")
        @Pattern(
            regexp = "^\\+855\\d{8,9}$",
            message = "Phone number must be Cambodian format (+855 followed by 8 or 9 digits)")
        String phone) {}
