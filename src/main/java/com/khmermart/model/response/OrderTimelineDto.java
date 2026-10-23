package com.khmermart.model.response;

import java.time.Instant;

public record OrderTimelineDto(String status, String note, Instant at) {}
