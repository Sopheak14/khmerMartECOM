package com.khmermart.service;

import com.khmermart.model.response.PublicSettingsDto;

public interface SettingService {

  PublicSettingsDto getPublicSettings();

  String getSettingValue(String key, String defaultValue);
}
