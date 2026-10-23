package com.khmermart.model.request;

import com.khmermart.model.enumerate.ShipmentStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AddTrackingEventRequest(
    @NotNull(message = "Shipment status is required") ShipmentStatus status,
    @Size(max = 255, message = "Location cannot exceed 255 characters") String location,
    @Size(max = 255, message = "Note cannot exceed 255 characters") String note) {}
