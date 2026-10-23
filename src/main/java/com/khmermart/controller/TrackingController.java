package com.khmermart.controller;

import com.khmermart.common.response.SuccessResponse;
import com.khmermart.model.response.TrackingDto;
import com.khmermart.service.TrackingService;
import com.khmermart.util.ApiResponseUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/track")
@Tag(name = "Tracking", description = "Public shipment tracking operations")
public class TrackingController {

  private final TrackingService trackingService;

  public TrackingController(TrackingService trackingService) {
    this.trackingService = trackingService;
  }

  @GetMapping("/{trackingNumber}")
  @Operation(summary = "Track shipment by tracking number (public)")
  public SuccessResponse<TrackingDto> getTracking(@PathVariable String trackingNumber) {
    TrackingDto dto = trackingService.getTracking(trackingNumber);
    return ApiResponseUtil.success(dto);
  }
}
