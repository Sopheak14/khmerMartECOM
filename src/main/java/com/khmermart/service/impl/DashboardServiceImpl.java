package com.khmermart.service.impl;

import com.khmermart.model.response.DashboardDto;
import com.khmermart.model.response.DashboardDto.DailySalesDto;
import com.khmermart.model.response.DashboardDto.PaymentMethodBreakdownDto;
import com.khmermart.model.response.DashboardDto.RecentOrderDto;
import com.khmermart.model.response.DashboardDto.TopProductDto;
import com.khmermart.service.DashboardService;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DashboardServiceImpl implements DashboardService {

  private static final ZoneId PHNOM_PENH = ZoneId.of("Asia/Phnom_Penh");

  private final JdbcTemplate jdbcTemplate;

  public DashboardServiceImpl(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  @Override
  @Cacheable(value = "adminDashboard", key = "#range != null ? #range : '30d'")
  @Transactional(readOnly = true)
  public DashboardDto getDashboard(String range) {
    Instant startDate = calculateStartDate(range);
    Timestamp startTimestamp = Timestamp.from(startDate);

    // 1. Revenue & Orders in range
    String revenueSql =
        """
        SELECT COALESCE(SUM(total_cents), 0) AS revenue, COUNT(*) AS cnt
        FROM orders
        WHERE placed_at >= ?
          AND status NOT IN ('CANCELLED')
          AND (payment_status = 'PAID' OR status IN ('CONFIRMED', 'SHIPPED', 'DELIVERED', 'OUT_FOR_DELIVERY'))
        """;

    long[] revenueAndOrders =
        jdbcTemplate.queryForObject(
            revenueSql,
            (rs, rowNum) -> new long[] {rs.getLong("revenue"), rs.getLong("cnt")},
            startTimestamp);

    long revenueUsdCents = revenueAndOrders != null ? revenueAndOrders[0] : 0L;
    long ordersCount = revenueAndOrders != null ? revenueAndOrders[1] : 0L;
    long aovCents = ordersCount > 0 ? (revenueUsdCents / ordersCount) : 0L;

    // 2. Pending Payments
    String pendingPaymentsSql = "SELECT COUNT(*) FROM payments WHERE status = 'PENDING'";
    Long pendingPayments = jdbcTemplate.queryForObject(pendingPaymentsSql, Long.class);
    long pendingPaymentsCount = pendingPayments != null ? pendingPayments : 0L;

    // 3. Low stock count
    String lowStockSql =
        "SELECT COUNT(*) FROM inventory WHERE (quantity_on_hand - quantity_reserved) <= low_stock_threshold";
    Long lowStock = jdbcTemplate.queryForObject(lowStockSql, Long.class);
    long lowStockCount = lowStock != null ? lowStock : 0L;

    // 4. Recent orders
    String recentOrdersSql =
        """
        SELECT o.id, o.order_number, COALESCE(u.full_name, 'Guest Customer') AS customer_name,
               o.total_cents, o.status, o.placed_at
        FROM orders o
        LEFT JOIN users u ON o.user_id = u.id
        ORDER BY o.placed_at DESC
        LIMIT 5
        """;

    List<RecentOrderDto> recentOrders =
        jdbcTemplate.query(
            recentOrdersSql,
            (rs, rowNum) ->
                new RecentOrderDto(
                    rs.getLong("id"),
                    rs.getString("order_number"),
                    rs.getString("customer_name"),
                    rs.getLong("total_cents"),
                    rs.getString("status"),
                    rs.getTimestamp("placed_at").toInstant()));

    // 5. Daily sales chart
    String salesChartSql =
        """
        SELECT TO_CHAR(date_trunc('day', placed_at AT TIME ZONE 'Asia/Phnom_Penh'), 'YYYY-MM-DD') AS day,
               COALESCE(SUM(total_cents), 0) AS revenue,
               COUNT(*) AS orders_count
        FROM orders
        WHERE placed_at >= ?
          AND status NOT IN ('CANCELLED')
          AND (payment_status = 'PAID' OR status IN ('CONFIRMED', 'SHIPPED', 'DELIVERED', 'OUT_FOR_DELIVERY'))
        GROUP BY day
        ORDER BY day ASC
        """;

    List<DailySalesDto> salesChart =
        jdbcTemplate.query(
            salesChartSql,
            (rs, rowNum) ->
                new DailySalesDto(
                    rs.getString("day"), rs.getLong("revenue"), rs.getLong("orders_count")),
            startTimestamp);

    // 6. Top products
    String topProductsSql =
        """
        SELECT p.id, p.name, COALESCE(SUM(oi.quantity), 0) AS units_sold,
               COALESCE(SUM(oi.line_total_cents), 0) AS revenue
        FROM order_items oi
        JOIN products p ON oi.product_id = p.id
        JOIN orders o ON oi.order_id = o.id
        WHERE o.placed_at >= ?
          AND o.status NOT IN ('CANCELLED')
        GROUP BY p.id, p.name
        ORDER BY units_sold DESC
        LIMIT 5
        """;

    List<TopProductDto> topProducts =
        jdbcTemplate.query(
            topProductsSql,
            (rs, rowNum) ->
                new TopProductDto(
                    rs.getLong("id"),
                    rs.getString("name"),
                    rs.getLong("units_sold"),
                    rs.getLong("revenue")),
            startTimestamp);

    // 7. Payment method breakdown
    String paymentMethodSql =
        """
        SELECT o.payment_method, COUNT(*) AS cnt, COALESCE(SUM(o.total_cents), 0) AS total_cents
        FROM orders o
        WHERE o.placed_at >= ?
          AND o.status NOT IN ('CANCELLED')
        GROUP BY o.payment_method
        ORDER BY total_cents DESC
        """;

    List<PaymentMethodBreakdownDto> paymentMethodBreakdown =
        jdbcTemplate.query(
            paymentMethodSql,
            (rs, rowNum) ->
                new PaymentMethodBreakdownDto(
                    rs.getString("payment_method"), rs.getLong("cnt"), rs.getLong("total_cents")),
            startTimestamp);

    return new DashboardDto(
        revenueUsdCents,
        ordersCount,
        aovCents,
        pendingPaymentsCount,
        lowStockCount,
        recentOrders,
        salesChart,
        topProducts,
        paymentMethodBreakdown);
  }

  private Instant calculateStartDate(String range) {
    if (range == null) {
      return Instant.now().minus(30, ChronoUnit.DAYS);
    }
    String normalized = range.trim().toLowerCase();
    if ("today".equals(normalized)) {
      return ZonedDateTime.now(PHNOM_PENH).truncatedTo(ChronoUnit.DAYS).toInstant();
    } else if ("7d".equals(normalized)) {
      return Instant.now().minus(7, ChronoUnit.DAYS);
    } else {
      return Instant.now().minus(30, ChronoUnit.DAYS);
    }
  }
}
