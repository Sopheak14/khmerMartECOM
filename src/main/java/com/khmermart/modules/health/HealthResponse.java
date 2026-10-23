package com.khmermart.modules.health;

import com.khmermart.modules.khqr.model.KhqrMode;

public record HealthResponse(String status, String db, String redis, KhqrMode khqrMode) {}
