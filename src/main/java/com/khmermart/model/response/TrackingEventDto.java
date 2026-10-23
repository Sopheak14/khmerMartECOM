package com.khmermart.model.response;

import com.khmermart.model.enumerate.ShipmentStatus;
import java.time.Instant;

public record TrackingEventDto(ShipmentStatus status, String location, String note, Instant at) {}
