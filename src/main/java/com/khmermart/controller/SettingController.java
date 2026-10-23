package com.khmermart.controller;

import com.khmermart.common.response.SuccessResponse;
import com.khmermart.model.response.PublicSettingsDto;
import com.khmermart.service.SettingService;
import com.khmermart.util.ApiResponseUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/settings")
@Tag(name = "Settings", description = "Public store configurations and rates")
public class SettingController {

  private final SettingService settingService;

  public SettingController(SettingService settingService) {
    this.settingService = settingService;
  }

  @GetMapping("/public")
  @Operation(summary = "Get public store settings and rates")
  public SuccessResponse<PublicSettingsDto> getPublicSettings() {
    return ApiResponseUtil.success(settingService.getPublicSettings());
  }
}
