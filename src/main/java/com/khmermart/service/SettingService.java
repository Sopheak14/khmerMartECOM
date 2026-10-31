package com.khmermart.service;

import com.khmermart.model.request.AdminSettingsUpdateRequest;
import com.khmermart.model.response.AdminSettingsDto;
import com.khmermart.model.response.PublicSettingsDto;

public interface SettingService {

  PublicSettingsDto getPublicSettings();

  String getSettingValue(String key, String defaultValue);

  /** Current KHQR mode (LIVE/MOCK): Admin Settings value if set, else the startup env default. */
  String getKhqrMode();

  AdminSettingsDto getAdminSettings();

  AdminSettingsDto updateAdminSettings(AdminSettingsUpdateRequest request, Long adminId, String ip);
}
