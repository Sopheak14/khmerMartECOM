package com.khmermart.model.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record AddressRequest(
    @NotBlank(message = "Label is required")
        @Size(max = 100, message = "Label cannot exceed 100 characters")
        String label,
    @NotBlank(message = "Recipient name is required")
        @Size(max = 255, message = "Recipient name cannot exceed 255 characters")
        String recipientName,
    @NotBlank(message = "Phone number is required")
        @Pattern(
            regexp = "^\\+855\\d{8,9}$",
            message = "Phone number must be Cambodian format (+855 followed by 8 or 9 digits)")
        String phone,
    @NotBlank(message = "Address line 1 is required")
        @Size(max = 255, message = "Line 1 cannot exceed 255 characters")
        String line1,
    @Size(max = 255, message = "Line 2 cannot exceed 255 characters") String line2,
    @NotBlank(message = "District is required")
        @Size(max = 100, message = "District cannot exceed 100 characters")
        String district,
    @NotBlank(message = "Province is required")
        @Size(max = 100, message = "Province cannot exceed 100 characters")
        String province,
    @Size(max = 50, message = "Postal code cannot exceed 50 characters") String postalCode,
    @JsonProperty("isDefault") boolean isDefault) {}
