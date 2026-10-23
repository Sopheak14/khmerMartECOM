package com.khmermart.model.response;

import java.time.Instant;

public record NotificationDto(
    Long id,
    String type,
    String title,
    String body,
    String link,
    Instant readAt,
    Instant createdAt) {}
