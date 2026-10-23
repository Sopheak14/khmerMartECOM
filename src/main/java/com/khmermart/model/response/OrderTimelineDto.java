package com.khmermart.model.response;

import java.time.Instant;

public record OrderTimelineDto(String status, Instant timestamp, String note) {}
