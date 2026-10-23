package com.khmermart.event;

public record NewLoginEvent(Long userId, String email, String ip, String userAgent) {}
