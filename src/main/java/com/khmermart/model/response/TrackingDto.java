package com.khmermart.model.response;

import com.khmermart.model.enumerate.ShipmentStatus;
import java.time.Instant;
import java.util.List;

public record TrackingDto(
    String trackingNumber,
    String carrier,
    String orderNumber,
    ShipmentStatus status,
    List<TrackingEventDto> events,
    Instant estimatedDelivery) {}
