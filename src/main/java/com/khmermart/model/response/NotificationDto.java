package com.khmermart.model.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record NotificationDto(
    Long id,
    String type,
    String title,
    String body,
    String link,
    Instant readAt,
    Instant createdAt) {}
