package com.khmermart.model.request;

import com.khmermart.model.response.ShippingMethodDto;
import java.util.List;

public record AdminSettingsUpdateRequest(
    String storeName, Long khrRate, String khqrMode, List<ShippingMethodDto> shippingMethods) {}
