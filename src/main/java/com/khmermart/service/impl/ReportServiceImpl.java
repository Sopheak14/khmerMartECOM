package com.khmermart.service.impl;

import com.khmermart.common.exception.CustomException;
import com.khmermart.common.exception.ErrorCode;
import com.khmermart.model.response.DashboardDto.DailySalesDto;
import com.khmermart.model.response.DashboardDto.PaymentMethodBreakdownDto;
import com.khmermart.model.response.DashboardDto.TopProductDto;
import com.khmermart.model.response.SalesGroupDto;
import com.khmermart.service.ReportService;
import java.io.ByteArrayOutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReportServiceImpl implements ReportService {

  private static final ZoneId PHNOM_PENH = ZoneId.of("Asia/Phnom_Penh");
  private final JdbcTemplate jdbcTemplate;

  public ReportServiceImpl(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  @Override
  @Transactional(readOnly = true)
  public long calculateNetRevenue(Instant from, Instant to) {
    String sql =
        """
        SELECT COALESCE(SUM(o.total_cents - COALESCE(r.refunded_cents, 0)), 0) AS net_revenue
        FROM orders o
        LEFT JOIN LATERAL (
          SELECT MAX(p.paid_at) AS paid_at
          FROM payments p
          WHERE p.order_id = o.id AND p.status IN ('PAID', 'PARTIALLY_REFUNDED')
        ) p_paid ON true
        LEFT JOIN LATERAL (
          SELECT COALESCE(SUM(ref.amount_cents), 0) AS refunded_cents
          FROM refunds ref
          JOIN payments p ON ref.payment_id = p.id
          WHERE p.order_id = o.id
        ) r ON true
        WHERE o.status != 'CANCELLED'
          AND o.payment_status IN ('PAID', 'PARTIALLY_REFUNDED')
          AND (cast(? as timestamptz) IS NULL OR COALESCE(p_paid.paid_at, o.placed_at) >= ?)
          AND (cast(? as timestamptz) IS NULL OR COALESCE(p_paid.paid_at, o.placed_at) <= ?)
        """;

    Timestamp fromTs = (from != null) ? Timestamp.from(from) : null;
    Timestamp toTs = (to != null) ? Timestamp.from(to) : null;

    Long net = jdbcTemplate.queryForObject(sql, Long.class, fromTs, fromTs, toTs, toTs);
    return net != null ? net : 0L;
  }

  @Override
  @Transactional(readOnly = true)
  public long countPaidOrders(Instant from, Instant to) {
    String sql =
        """
        SELECT COUNT(o.id) AS cnt
        FROM orders o
        LEFT JOIN LATERAL (
          SELECT MAX(p.paid_at) AS paid_at
          FROM payments p
          WHERE p.order_id = o.id AND p.status IN ('PAID', 'PARTIALLY_REFUNDED')
        ) p_paid ON true
        WHERE o.status != 'CANCELLED'
          AND o.payment_status IN ('PAID', 'PARTIALLY_REFUNDED')
          AND (cast(? as timestamptz) IS NULL OR COALESCE(p_paid.paid_at, o.placed_at) >= ?)
          AND (cast(? as timestamptz) IS NULL OR COALESCE(p_paid.paid_at, o.placed_at) <= ?)
        """;

    Timestamp fromTs = (from != null) ? Timestamp.from(from) : null;
    Timestamp toTs = (to != null) ? Timestamp.from(to) : null;

    Long cnt = jdbcTemplate.queryForObject(sql, Long.class, fromTs, fromTs, toTs, toTs);
    return cnt != null ? cnt : 0L;
  }

  @Override
  @Transactional(readOnly = true)
  public List<DailySalesDto> getDailySalesChart(int days, LocalDate endDate) {
    LocalDate startDate = endDate.minusDays(days - 1);
    Instant fromInstant = startDate.atStartOfDay(PHNOM_PENH).toInstant();
    Instant toInstant = endDate.plusDays(1).atStartOfDay(PHNOM_PENH).toInstant();

    String sql =
        """
        SELECT
          TO_CHAR(COALESCE(p_paid.paid_at, o.placed_at) AT TIME ZONE 'Asia/Phnom_Penh', 'YYYY-MM-DD') AS day,
          COUNT(o.id) AS orders,
          COALESCE(SUM(o.total_cents - COALESCE(r.refunded_cents, 0)), 0) AS revenue_cents
        FROM orders o
        LEFT JOIN LATERAL (
          SELECT MAX(p.paid_at) AS paid_at
          FROM payments p
          WHERE p.order_id = o.id AND p.status IN ('PAID', 'PARTIALLY_REFUNDED')
        ) p_paid ON true
        LEFT JOIN LATERAL (
          SELECT COALESCE(SUM(ref.amount_cents), 0) AS refunded_cents
          FROM refunds ref
          JOIN payments p ON ref.payment_id = p.id
          WHERE p.order_id = o.id
        ) r ON true
        WHERE o.status != 'CANCELLED'
          AND o.payment_status IN ('PAID', 'PARTIALLY_REFUNDED')
          AND COALESCE(p_paid.paid_at, o.placed_at) >= ?
          AND COALESCE(p_paid.paid_at, o.placed_at) < ?
        GROUP BY day
        ORDER BY day ASC
        """;

    Map<String, DailySalesDto> map = new HashMap<>();
    jdbcTemplate.query(
        sql,
        rs -> {
          String day = rs.getString("day");
          long orders = rs.getLong("orders");
          long rev = rs.getLong("revenue_cents");
          map.put(day, new DailySalesDto(day, rev, orders));
        },
        Timestamp.from(fromInstant),
        Timestamp.from(toInstant));

    List<DailySalesDto> result = new ArrayList<>();
    LocalDate current = startDate;
    while (!current.isAfter(endDate)) {
      String key = current.toString();
      result.add(map.getOrDefault(key, new DailySalesDto(key, 0L, 0L)));
      current = current.plusDays(1);
    }
    return result;
  }

  @Override
  @Transactional(readOnly = true)
  public List<PaymentMethodBreakdownDto> getPaymentMethodBreakdown(Instant from, Instant to) {
    String sql =
        """
        SELECT
          o.payment_method AS method,
          COUNT(o.id) AS count,
          COALESCE(SUM(o.total_cents - COALESCE(r.refunded_cents, 0)), 0) AS amount_cents
        FROM orders o
        LEFT JOIN LATERAL (
          SELECT MAX(p.paid_at) AS paid_at
          FROM payments p
          WHERE p.order_id = o.id AND p.status IN ('PAID', 'PARTIALLY_REFUNDED')
        ) p_paid ON true
        LEFT JOIN LATERAL (
          SELECT COALESCE(SUM(ref.amount_cents), 0) AS refunded_cents
          FROM refunds ref
          JOIN payments p ON ref.payment_id = p.id
          WHERE p.order_id = o.id
        ) r ON true
        WHERE o.status != 'CANCELLED'
          AND o.payment_status IN ('PAID', 'PARTIALLY_REFUNDED')
          AND (cast(? as timestamptz) IS NULL OR COALESCE(p_paid.paid_at, o.placed_at) >= ?)
          AND (cast(? as timestamptz) IS NULL OR COALESCE(p_paid.paid_at, o.placed_at) < ?)
        GROUP BY o.payment_method
        ORDER BY amount_cents DESC
        """;

    Timestamp fromTs = (from != null) ? Timestamp.from(from) : null;
    Timestamp toTs = (to != null) ? Timestamp.from(to) : null;

    return jdbcTemplate.query(
        sql,
        (rs, rowNum) ->
            new PaymentMethodBreakdownDto(
                rs.getString("method"), rs.getLong("count"), rs.getLong("amount_cents")),
        fromTs,
        fromTs,
        toTs,
        toTs);
  }

  @Override
  @Transactional(readOnly = true)
  public List<TopProductDto> getTopProducts(Instant from, Instant to, int limit) {
    String sql =
        """
        SELECT
          p.id AS product_id,
          p.name AS product_name,
          COALESCE(SUM(oi.quantity), 0) AS sold_qty,
          COALESCE(SUM(oi.line_total_cents), 0) AS revenue_cents
        FROM order_items oi
        JOIN products p ON oi.product_id = p.id
        JOIN orders o ON oi.order_id = o.id
        LEFT JOIN LATERAL (
          SELECT MAX(p.paid_at) AS paid_at
          FROM payments p
          WHERE p.order_id = o.id AND p.status IN ('PAID', 'PARTIALLY_REFUNDED')
        ) p_paid ON true
        WHERE o.status != 'CANCELLED'
          AND o.payment_status IN ('PAID', 'PARTIALLY_REFUNDED')
          AND (cast(? as timestamptz) IS NULL OR COALESCE(p_paid.paid_at, o.placed_at) >= ?)
          AND (cast(? as timestamptz) IS NULL OR COALESCE(p_paid.paid_at, o.placed_at) < ?)
        GROUP BY p.id, p.name
        ORDER BY sold_qty DESC, revenue_cents DESC
        LIMIT ?
        """;

    Timestamp fromTs = (from != null) ? Timestamp.from(from) : null;
    Timestamp toTs = (to != null) ? Timestamp.from(to) : null;

    return jdbcTemplate.query(
        sql,
        (rs, rowNum) ->
            new TopProductDto(
                rs.getLong("product_id"),
                rs.getString("product_name"),
                rs.getLong("sold_qty"),
                rs.getLong("revenue_cents")),
        fromTs,
        fromTs,
        toTs,
        toTs,
        limit);
  }

  @Override
  @Transactional(readOnly = true)
  public List<SalesGroupDto> getSalesReport(String groupBy, Instant from, Instant to) {
    String normalized = (groupBy != null) ? groupBy.trim().toLowerCase(Locale.ROOT) : "day";
    String sql = buildQuery(normalized);

    Timestamp fromTs = (from != null) ? Timestamp.from(from) : null;
    Timestamp toTs = (to != null) ? Timestamp.from(to) : null;

    return jdbcTemplate.query(
        sql,
        (rs, rowNum) -> {
          String grp = rs.getString("grp");
          long ordersCount = rs.getLong("orders_count");
          long unitsSold = rs.getLong("units_sold");
          long revenueCents = rs.getLong("revenue_cents");
          String revenueUsd = String.format(Locale.US, "$%.2f", revenueCents / 100.0);
          return new SalesGroupDto(grp, ordersCount, unitsSold, revenueCents, revenueUsd);
        },
        fromTs,
        fromTs,
        toTs,
        toTs);
  }

  @Override
  @Transactional(readOnly = true)
  public byte[] exportSalesCsv(String groupBy, Instant from, Instant to) {
    List<SalesGroupDto> rows = getSalesReport(groupBy, from, to);
    String groupHeader = resolveGroupHeader(groupBy);

    try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
        OutputStreamWriter writer = new OutputStreamWriter(baos, StandardCharsets.UTF_8);
        CSVPrinter printer =
            new CSVPrinter(
                writer,
                CSVFormat.DEFAULT
                    .builder()
                    .setHeader(groupHeader, "OrdersCount", "UnitsSold", "RevenueUSD")
                    .build())) {

      for (SalesGroupDto row : rows) {
        printer.printRecord(
            row.groupKey(),
            row.ordersCount(),
            row.unitsSold(),
            String.format(Locale.US, "%.2f", row.revenueCents() / 100.0));
      }
      printer.flush();
      return baos.toByteArray();
    } catch (Exception e) {
      throw new CustomException(
          ErrorCode.INTERNAL_ERROR, "Failed to generate sales CSV: " + e.getMessage());
    }
  }

  @Override
  @Transactional(readOnly = true)
  public byte[] exportSalesXlsx(String groupBy, Instant from, Instant to) {
    List<SalesGroupDto> rows = getSalesReport(groupBy, from, to);
    String groupHeader = resolveGroupHeader(groupBy);

    try (Workbook workbook = new XSSFWorkbook();
        ByteArrayOutputStream baos = new ByteArrayOutputStream()) {

      Sheet sheet = workbook.createSheet("Sales Report");

      CellStyle headerStyle = workbook.createCellStyle();
      Font font = workbook.createFont();
      font.setBold(true);
      headerStyle.setFont(font);

      Row headerRow = sheet.createRow(0);
      String[] headers = {groupHeader, "Orders Count", "Units Sold", "Revenue (USD)"};
      for (int i = 0; i < headers.length; i++) {
        Cell cell = headerRow.createCell(i);
        cell.setCellValue(headers[i]);
        cell.setCellStyle(headerStyle);
      }

      int rowIdx = 1;
      for (SalesGroupDto item : rows) {
        Row row = sheet.createRow(rowIdx++);
        row.createCell(0).setCellValue(item.groupKey());
        row.createCell(1).setCellValue(item.ordersCount());
        row.createCell(2).setCellValue(item.unitsSold());
        row.createCell(3).setCellValue(item.revenueCents() / 100.0);
      }

      for (int i = 0; i < headers.length; i++) {
        sheet.autoSizeColumn(i);
      }

      workbook.write(baos);
      return baos.toByteArray();
    } catch (Exception e) {
      throw new CustomException(
          ErrorCode.INTERNAL_ERROR, "Failed to generate sales Excel file: " + e.getMessage());
    }
  }

  private String buildQuery(String groupBy) {
    return switch (groupBy) {
      case "month" ->
          """
          SELECT
            TO_CHAR(COALESCE(p_paid.paid_at, o.placed_at) AT TIME ZONE 'Asia/Phnom_Penh', 'YYYY-MM') AS grp,
            COUNT(DISTINCT o.id) AS orders_count,
            COALESCE(SUM(oi.quantity), 0) AS units_sold,
            SUM(o.total_cents - COALESCE(r.refunded_cents, 0)) AS revenue_cents
          FROM orders o
          LEFT JOIN (
            SELECT order_id, SUM(quantity) AS quantity FROM order_items GROUP BY order_id
          ) oi ON o.id = oi.order_id
          LEFT JOIN LATERAL (
            SELECT MAX(p.paid_at) AS paid_at
            FROM payments p
            WHERE p.order_id = o.id AND p.status IN ('PAID', 'PARTIALLY_REFUNDED')
          ) p_paid ON true
          LEFT JOIN LATERAL (
            SELECT COALESCE(SUM(ref.amount_cents), 0) AS refunded_cents
            FROM refunds ref
            JOIN payments p ON ref.payment_id = p.id
            WHERE p.order_id = o.id
          ) r ON true
          WHERE o.status != 'CANCELLED'
            AND o.payment_status IN ('PAID', 'PARTIALLY_REFUNDED')
            AND (cast(? as timestamptz) IS NULL OR COALESCE(p_paid.paid_at, o.placed_at) >= ?)
            AND (cast(? as timestamptz) IS NULL OR COALESCE(p_paid.paid_at, o.placed_at) <= ?)
          GROUP BY grp
          ORDER BY grp DESC
          """;
      case "category" ->
          """
          SELECT
            COALESCE(c.name, 'Uncategorized') AS grp,
            COUNT(DISTINCT o.id) AS orders_count,
            COALESCE(SUM(oi.quantity), 0) AS units_sold,
            COALESCE(SUM(oi.line_total_cents), 0) AS revenue_cents
          FROM orders o
          JOIN order_items oi ON o.id = oi.order_id
          LEFT JOIN products p ON oi.product_id = p.id
          LEFT JOIN categories c ON p.category_id = c.id
          LEFT JOIN LATERAL (
            SELECT MAX(pay.paid_at) AS paid_at
            FROM payments pay
            WHERE pay.order_id = o.id AND pay.status IN ('PAID', 'PARTIALLY_REFUNDED')
          ) p_paid ON true
          WHERE o.status != 'CANCELLED'
            AND o.payment_status IN ('PAID', 'PARTIALLY_REFUNDED')
            AND (cast(? as timestamptz) IS NULL OR COALESCE(p_paid.paid_at, o.placed_at) >= ?)
            AND (cast(? as timestamptz) IS NULL OR COALESCE(p_paid.paid_at, o.placed_at) <= ?)
          GROUP BY grp
          ORDER BY revenue_cents DESC
          """;
      case "product" ->
          """
          SELECT
            p.name AS grp,
            COUNT(DISTINCT o.id) AS orders_count,
            COALESCE(SUM(oi.quantity), 0) AS units_sold,
            COALESCE(SUM(oi.line_total_cents), 0) AS revenue_cents
          FROM orders o
          JOIN order_items oi ON o.id = oi.order_id
          JOIN products p ON oi.product_id = p.id
          LEFT JOIN LATERAL (
            SELECT MAX(pay.paid_at) AS paid_at
            FROM payments pay
            WHERE pay.order_id = o.id AND pay.status IN ('PAID', 'PARTIALLY_REFUNDED')
          ) p_paid ON true
          WHERE o.status != 'CANCELLED'
            AND o.payment_status IN ('PAID', 'PARTIALLY_REFUNDED')
            AND (cast(? as timestamptz) IS NULL OR COALESCE(p_paid.paid_at, o.placed_at) >= ?)
            AND (cast(? as timestamptz) IS NULL OR COALESCE(p_paid.paid_at, o.placed_at) <= ?)
          GROUP BY grp
          ORDER BY revenue_cents DESC
          """;
      case "paymentmethod" ->
          """
          SELECT
            o.payment_method AS grp,
            COUNT(DISTINCT o.id) AS orders_count,
            COALESCE(SUM(oi.quantity), 0) AS units_sold,
            SUM(o.total_cents - COALESCE(r.refunded_cents, 0)) AS revenue_cents
          FROM orders o
          LEFT JOIN (
            SELECT order_id, SUM(quantity) AS quantity FROM order_items GROUP BY order_id
          ) oi ON o.id = oi.order_id
          LEFT JOIN LATERAL (
            SELECT MAX(p.paid_at) AS paid_at
            FROM payments p
            WHERE p.order_id = o.id AND p.status IN ('PAID', 'PARTIALLY_REFUNDED')
          ) p_paid ON true
          LEFT JOIN LATERAL (
            SELECT COALESCE(SUM(ref.amount_cents), 0) AS refunded_cents
            FROM refunds ref
            JOIN payments p ON ref.payment_id = p.id
            WHERE p.order_id = o.id
          ) r ON true
          WHERE o.status != 'CANCELLED'
            AND o.payment_status IN ('PAID', 'PARTIALLY_REFUNDED')
            AND (cast(? as timestamptz) IS NULL OR COALESCE(p_paid.paid_at, o.placed_at) >= ?)
            AND (cast(? as timestamptz) IS NULL OR COALESCE(p_paid.paid_at, o.placed_at) <= ?)
          GROUP BY grp
          ORDER BY revenue_cents DESC
          """;
      default ->
          """
          SELECT
            TO_CHAR(COALESCE(p_paid.paid_at, o.placed_at) AT TIME ZONE 'Asia/Phnom_Penh', 'YYYY-MM-DD') AS grp,
            COUNT(DISTINCT o.id) AS orders_count,
            COALESCE(SUM(oi.quantity), 0) AS units_sold,
            SUM(o.total_cents - COALESCE(r.refunded_cents, 0)) AS revenue_cents
          FROM orders o
          LEFT JOIN (
            SELECT order_id, SUM(quantity) AS quantity FROM order_items GROUP BY order_id
          ) oi ON o.id = oi.order_id
          LEFT JOIN LATERAL (
            SELECT MAX(p.paid_at) AS paid_at
            FROM payments p
            WHERE p.order_id = o.id AND p.status IN ('PAID', 'PARTIALLY_REFUNDED')
          ) p_paid ON true
          LEFT JOIN LATERAL (
            SELECT COALESCE(SUM(ref.amount_cents), 0) AS refunded_cents
            FROM refunds ref
            JOIN payments p ON ref.payment_id = p.id
            WHERE p.order_id = o.id
          ) r ON true
          WHERE o.status != 'CANCELLED'
            AND o.payment_status IN ('PAID', 'PARTIALLY_REFUNDED')
            AND (cast(? as timestamptz) IS NULL OR COALESCE(p_paid.paid_at, o.placed_at) >= ?)
            AND (cast(? as timestamptz) IS NULL OR COALESCE(p_paid.paid_at, o.placed_at) <= ?)
          GROUP BY grp
          ORDER BY grp DESC
          """;
    };
  }

  private String resolveGroupHeader(String groupBy) {
    if (groupBy == null) return "Day";
    return switch (groupBy.trim().toLowerCase(Locale.ROOT)) {
      case "month" -> "Month";
      case "category" -> "Category";
      case "product" -> "Product";
      case "paymentmethod" -> "PaymentMethod";
      default -> "Day";
    };
  }
}
