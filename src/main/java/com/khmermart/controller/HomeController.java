package com.khmermart.controller;

import com.khmermart.common.response.SuccessResponse;
import com.khmermart.model.response.HomeDto;
import com.khmermart.service.HomeService;
import com.khmermart.util.ApiResponseUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/home")
@Tag(name = "Home", description = "Home page aggregator APIs")
public class HomeController {

  private final HomeService homeService;

  public HomeController(HomeService homeService) {
    this.homeService = homeService;
  }

  @GetMapping
  @Operation(summary = "Get home page aggregated feed")
  public SuccessResponse<HomeDto> getHome() {
    return ApiResponseUtil.success(homeService.getHomeData());
  }
}
