package com.khmermart.modules.address.dto;

public record AddressDTO(
    Long id,
    String label,
    String recipientName,
    String phone,
    String line1,
    String line2,
    String district,
    String province,
    String postalCode,
    boolean isDefault) {}
