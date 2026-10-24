package com.khmermart.service;

import com.khmermart.model.response.DashboardDto;

public interface DashboardService {

  DashboardDto getDashboard(String range);
}
