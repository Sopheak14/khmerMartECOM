package com.khmermart.service;

import com.khmermart.model.response.SalesGroupDto;
import java.time.Instant;
import java.util.List;

public interface ReportService {

  List<SalesGroupDto> getSalesReport(String groupBy, Instant from, Instant to);

  byte[] exportSalesCsv(String groupBy, Instant from, Instant to);

  byte[] exportSalesXlsx(String groupBy, Instant from, Instant to);
}
