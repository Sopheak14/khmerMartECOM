package com.khmermart.model.response;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record SalesGroupDto(
    String groupKey, long ordersCount, long unitsSold, long revenueCents, String revenueUsd) {}
