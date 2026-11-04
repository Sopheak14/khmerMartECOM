package com.khmermart.model.response;

import java.math.BigDecimal;

public record AddressDto(
    Long id,
    String label,
    String recipientName,
    String phone,
    String line1,
    String line2,
    String district,
    String province,
    String postalCode,
    BigDecimal latitude,
    BigDecimal longitude,
    boolean isDefault) {}
