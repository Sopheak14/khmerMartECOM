package com.khmermart.model.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record AdminSettingsDto(
    String storeName, Long khrRate, String khqrMode, List<ShippingMethodDto> shippingMethods) {}
