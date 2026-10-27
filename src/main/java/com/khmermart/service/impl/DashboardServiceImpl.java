package com.khmermart.service.impl;

import com.khmermart.model.response.DashboardDto;
import com.khmermart.model.response.DashboardDto.DailySalesDto;
import com.khmermart.model.response.DashboardDto.LowStockItemDto;
import com.khmermart.model.response.DashboardDto.PaymentMethodBreakdownDto;
import com.khmermart.model.response.DashboardDto.TopProductDto;
import com.khmermart.model.response.OrderDto;
import com.khmermart.repository.OrderRepository;
import com.khmermart.service.DashboardService;
import com.khmermart.service.OrderService;
import com.khmermart.service.ReportService;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DashboardServiceImpl implements DashboardService {

  private static final ZoneId PHNOM_PENH = ZoneId.of("Asia/Phnom_Penh");

  private final ReportService reportService;
  private final OrderService orderService;
  private final OrderRepository orderRepository;
  private final JdbcTemplate jdbcTemplate;

  public DashboardServiceImpl(
      ReportService reportService,
      OrderService orderService,
      OrderRepository orderRepository,
      JdbcTemplate jdbcTemplate) {
    this.reportService = reportService;
    this.orderService = orderService;
    this.orderRepository = orderRepository;
    this.jdbcTemplate = jdbcTemplate;
  }

  @Override
  @Cacheable(value = "adminDashboard", key = "#range != null ? #range : '30d'")
  @Transactional(readOnly = true)
  public DashboardDto getDashboard(String range) {
    LocalDate today = LocalDate.now(PHNOM_PENH);
    Instant todayStart = today.atStartOfDay(PHNOM_PENH).toInstant();
    Instant todayEnd = today.plusDays(1).atStartOfDay(PHNOM_PENH).toInstant();

    Instant sevenDaysStart = today.minusDays(6).atStartOfDay(PHNOM_PENH).toInstant();
    Instant thirtyDaysStart = today.minusDays(29).atStartOfDay(PHNOM_PENH).toInstant();

    // 1. The three revenue KPIs are ALWAYS returned
    long revenueTodayCents = reportService.calculateNetRevenue(todayStart, todayEnd);
    long revenue7dCents = reportService.calculateNetRevenue(sevenDaysStart, todayEnd);
    long revenue30dCents = reportService.calculateNetRevenue(thirtyDaysStart, todayEnd);

    // 2. Orders today & orders pending
    long ordersToday = reportService.countPaidOrders(todayStart, todayEnd);
    String pendingOrdersSql =
        "SELECT COUNT(*) FROM orders WHERE status != 'CANCELLED' AND (status = 'PENDING' OR payment_status = 'PENDING')";
    Long pendingOrdersCount = jdbcTemplate.queryForObject(pendingOrdersSql, Long.class);
    long ordersPending = pendingOrdersCount != null ? pendingOrdersCount : 0L;

    // 3. Pending payments
    String pendingPaymentsSql = "SELECT COUNT(*) FROM payments WHERE status = 'PENDING'";
    Long pendingPaymentsRes = jdbcTemplate.queryForObject(pendingPaymentsSql, Long.class);
    long pendingPayments = pendingPaymentsRes != null ? pendingPaymentsRes : 0L;

    // 4. Determine range (today | 7d | 30d)
    int chartDays = 30;
    Instant rangeFrom = thirtyDaysStart;
    Instant rangeTo = todayEnd;
    if (range != null) {
      String normalized = range.trim().toLowerCase();
      if ("today".equals(normalized)) {
        chartDays = 1;
        rangeFrom = todayStart;
        rangeTo = todayEnd;
      } else if ("7d".equals(normalized)) {
        chartDays = 7;
        rangeFrom = sevenDaysStart;
        rangeTo = todayEnd;
      }
    }

    long rangeRevenue = reportService.calculateNetRevenue(rangeFrom, rangeTo);
    long rangeOrders = reportService.countPaidOrders(rangeFrom, rangeTo);
    long avgOrderValueCents = rangeOrders > 0 ? (rangeRevenue / rangeOrders) : 0L;

    // 5. Sales chart zero-filled
    List<DailySalesDto> salesChart = reportService.getDailySalesChart(chartDays, today);

    // 6. Top products in range
    List<TopProductDto> topProducts = reportService.getTopProducts(rangeFrom, rangeTo, 5);

    // 7. Payment method breakdown in range
    List<PaymentMethodBreakdownDto> paymentMethodBreakdown =
        reportService.getPaymentMethodBreakdown(rangeFrom, rangeTo);

    // 8. Low stock items
    String lowStockSql =
        """
        SELECT
          p.id AS product_id,
          p.name AS product_name,
          COALESCE(pv.sku, p.sku) AS sku,
          (i.quantity_on_hand - i.quantity_reserved) AS available,
          i.low_stock_threshold AS threshold
        FROM inventory i
        JOIN products p ON i.product_id = p.id
        LEFT JOIN product_variants pv ON i.variant_id = pv.id
        WHERE (i.quantity_on_hand - i.quantity_reserved) <= i.low_stock_threshold
        ORDER BY available ASC, p.id ASC
        """;
    List<LowStockItemDto> lowStock =
        jdbcTemplate.query(
            lowStockSql,
            (rs, rowNum) ->
                new LowStockItemDto(
                    rs.getLong("product_id"),
                    rs.getString("product_name"),
                    rs.getString("sku"),
                    rs.getInt("available"),
                    rs.getInt("threshold")));

    // 9. Recent orders with customer
    List<OrderDto> recentOrders =
        orderRepository
            .findAll(PageRequest.of(0, 5, Sort.by(Sort.Direction.DESC, "placedAt")))
            .stream()
            .map(orderService::toDto)
            .toList();

    return new DashboardDto(
        revenueTodayCents,
        revenue7dCents,
        revenue30dCents,
        ordersToday,
        ordersPending,
        avgOrderValueCents,
        pendingPayments,
        lowStock,
        recentOrders,
        salesChart,
        topProducts,
        paymentMethodBreakdown);
  }
}
