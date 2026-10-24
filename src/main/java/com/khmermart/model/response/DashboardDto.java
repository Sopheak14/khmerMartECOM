package com.khmermart.model.response;

import java.util.List;

public record DashboardDto(
    long revenueTodayCents,
    long revenue7dCents,
    long revenue30dCents,
    long ordersToday,
    long ordersPending,
    long avgOrderValueCents,
    long pendingPayments,
    List<LowStockItemDto> lowStock,
    List<OrderDto> recentOrders,
    List<DailySalesDto> salesChart,
    List<TopProductDto> topProducts,
    List<PaymentMethodBreakdownDto> paymentMethodBreakdown) {

  public record LowStockItemDto(
      Long productId,
      String name,
      String sku,
      int available,
      int threshold) {}

  public record DailySalesDto(String date, long revenueCents, long orders) {}

  public record TopProductDto(Long productId, String name, long soldQty, long revenueCents) {}

  public record PaymentMethodBreakdownDto(String method, long count, long amountCents) {}
}
