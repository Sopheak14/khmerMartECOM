package com.khmermart.model.response;

import com.khmermart.model.enumerate.PaymentMethod;
import java.util.List;

public record CheckoutPreviewDto(
    List<CartItemDto> items,
    Long subtotalCents,
    Long discountCents,
    Long shippingCents,
    Long totalCents,
    Long totalKhr,
    ShippingMethodDto shippingMethod,
    AddressDto address,
    List<PaymentMethod> availablePaymentMethods) {}
