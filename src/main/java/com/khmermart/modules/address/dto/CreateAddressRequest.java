package com.khmermart.modules.address.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record CreateAddressRequest(
    @NotBlank(message = "Label is required") String label,
    @NotBlank(message = "Recipient name is required") String recipientName,
    @NotBlank(message = "Phone number is required")
        @Pattern(
            regexp = "^\\+855\\d{8,9}$",
            message = "Phone number must start with +855 followed by 8 or 9 digits")
        String phone,
    @NotBlank(message = "Address line 1 is required") String line1,
    String line2,
    @NotBlank(message = "District is required") String district,
    @NotBlank(message = "Province is required") String province,
    String postalCode,
    Boolean isDefault) {}
