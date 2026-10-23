package com.khmermart.model.response;

public record ShippingMethodDto(
    String code,
    String name,
    String nameKm,
    Long feeCents,
    Long freeOverCents,
    int etaDaysMin,
    int etaDaysMax,
    boolean phnomPenhOnly) {}
