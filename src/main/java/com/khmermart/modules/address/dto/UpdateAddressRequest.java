package com.khmermart.modules.address.dto;

import jakarta.validation.constraints.Pattern;

public record UpdateAddressRequest(
    String label,
    String recipientName,
    @Pattern(
            regexp = "^\\+855\\d{8,9}$",
            message = "Phone number must start with +855 followed by 8 or 9 digits")
        String phone,
    String line1,
    String line2,
    String district,
    String province,
    String postalCode,
    Boolean isDefault) {}
