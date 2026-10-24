package com.khmermart.service;

import com.khmermart.model.response.DashboardDto.DailySalesDto;
import com.khmermart.model.response.DashboardDto.PaymentMethodBreakdownDto;
import com.khmermart.model.response.DashboardDto.TopProductDto;
import com.khmermart.model.response.SalesGroupDto;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public interface ReportService {

  List<SalesGroupDto> getSalesReport(String groupBy, Instant from, Instant to);

  byte[] exportSalesCsv(String groupBy, Instant from, Instant to);

  byte[] exportSalesXlsx(String groupBy, Instant from, Instant to);

  long calculateNetRevenue(Instant from, Instant to);

  long countPaidOrders(Instant from, Instant to);

  List<DailySalesDto> getDailySalesChart(int days, LocalDate endDate);

  List<PaymentMethodBreakdownDto> getPaymentMethodBreakdown(Instant from, Instant to);

  List<TopProductDto> getTopProducts(Instant from, Instant to, int limit);
}
