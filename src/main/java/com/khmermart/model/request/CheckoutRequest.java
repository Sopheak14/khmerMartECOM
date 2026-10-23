package com.khmermart.model.request;

import com.khmermart.model.enumerate.PaymentMethod;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CheckoutRequest(
    @NotNull(message = "Address ID is required") Long addressId,
    @NotBlank(message = "Shipping method code is required") String shippingMethodCode,
    @NotNull(message = "Payment method is required") PaymentMethod paymentMethod,
    String notes) {}
