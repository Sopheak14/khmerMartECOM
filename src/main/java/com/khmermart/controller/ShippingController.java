package com.khmermart.controller;

import com.khmermart.common.response.SuccessResponse;
import com.khmermart.model.response.ProvinceDto;
import com.khmermart.model.response.ShippingMethodDto;
import com.khmermart.service.ShippingService;
import com.khmermart.util.ApiResponseUtil;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/shipping")
public class ShippingController {

  private final ShippingService shippingService;

  public ShippingController(ShippingService shippingService) {
    this.shippingService = shippingService;
  }

  @GetMapping("/methods")
  public ResponseEntity<SuccessResponse<List<ShippingMethodDto>>> getMethods() {
    return ResponseEntity.ok(ApiResponseUtil.success(shippingService.getShippingMethods()));
  }

  @GetMapping("/provinces")
  public ResponseEntity<SuccessResponse<List<ProvinceDto>>> getProvinces() {
    return ResponseEntity.ok(ApiResponseUtil.success(shippingService.getProvinces()));
  }
}
