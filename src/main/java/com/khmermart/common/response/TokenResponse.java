package com.khmermart.common.response;

import com.khmermart.model.response.AppUserDto;

public record TokenResponse(String accessToken, AppUserDto user) {}
