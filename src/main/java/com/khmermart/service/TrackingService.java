package com.khmermart.service;

import com.khmermart.model.request.AddTrackingEventRequest;
import com.khmermart.model.response.TrackingDto;
import com.khmermart.model.response.TrackingEventDto;

public interface TrackingService {
  TrackingDto getTracking(String trackingNumber);

  TrackingEventDto addTrackingEvent(String orderNumber, AddTrackingEventRequest request);
}
