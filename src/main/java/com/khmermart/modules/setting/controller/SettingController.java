package com.khmermart.modules.setting.controller;

import com.khmermart.common.api.ApiResponse;
import com.khmermart.modules.setting.dto.PublicSettingsDTO;
import com.khmermart.modules.setting.service.SettingService;
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
  public ApiResponse<PublicSettingsDTO> getPublicSettings() {
    return ApiResponse.ok(settingService.getPublicSettings());
  }
}
