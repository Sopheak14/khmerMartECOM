package com.khmermart.model.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record DashboardDto(
    long revenueUsdCents,
    long ordersCount,
    long aovCents,
    long pendingPaymentsCount,
    long lowStockCount,
    List<RecentOrderDto> recentOrders,
    List<DailySalesDto> salesChart,
    List<TopProductDto> topProducts,
    List<PaymentMethodBreakdownDto> paymentMethodBreakdown) {

  public record RecentOrderDto(
      Long id,
      String orderNumber,
      String customerName,
      Long totalCents,
      String status,
      Instant placedAt) {}

  public record DailySalesDto(String date, long revenueCents, long ordersCount) {}

  public record TopProductDto(Long productId, String name, long unitsSold, long revenueCents) {}

  public record PaymentMethodBreakdownDto(String method, long count, long totalCents) {}
}
