package com.khmermart.service.impl;

import com.khmermart.common.exception.CustomException;
import com.khmermart.common.exception.ErrorCode;
import com.khmermart.model.response.SalesGroupDto;
import com.khmermart.service.ReportService;
import java.io.ByteArrayOutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
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

  private final JdbcTemplate jdbcTemplate;

  public ReportServiceImpl(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
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

      // Header style
      CellStyle headerStyle = workbook.createCellStyle();
      Font font = workbook.createFont();
      font.setBold(true);
      headerStyle.setFont(font);

      // Header row
      Row headerRow = sheet.createRow(0);
      String[] headers = {groupHeader, "Orders Count", "Units Sold", "Revenue (USD)"};
      for (int i = 0; i < headers.length; i++) {
        Cell cell = headerRow.createCell(i);
        cell.setCellValue(headers[i]);
        cell.setCellStyle(headerStyle);
      }

      // Data rows
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
          SELECT TO_CHAR(date_trunc('month', o.placed_at AT TIME ZONE 'Asia/Phnom_Penh'), 'YYYY-MM') AS grp,
                 COUNT(DISTINCT o.id) AS orders_count,
                 COALESCE(SUM(oi.quantity), 0) AS units_sold,
                 COALESCE(SUM(oi.line_total_cents), 0) AS revenue_cents
          FROM orders o
          JOIN order_items oi ON o.id = oi.order_id
          WHERE o.status NOT IN ('CANCELLED')
            AND (cast(? as timestamptz) IS NULL OR o.placed_at >= ?)
            AND (cast(? as timestamptz) IS NULL OR o.placed_at <= ?)
          GROUP BY grp
          ORDER BY grp DESC
          """;
      case "category" ->
          """
          SELECT COALESCE(c.name, 'Uncategorized') AS grp,
                 COUNT(DISTINCT o.id) AS orders_count,
                 COALESCE(SUM(oi.quantity), 0) AS units_sold,
                 COALESCE(SUM(oi.line_total_cents), 0) AS revenue_cents
          FROM orders o
          JOIN order_items oi ON o.id = oi.order_id
          LEFT JOIN products p ON oi.product_id = p.id
          LEFT JOIN categories c ON p.category_id = c.id
          WHERE o.status NOT IN ('CANCELLED')
            AND (cast(? as timestamptz) IS NULL OR o.placed_at >= ?)
            AND (cast(? as timestamptz) IS NULL OR o.placed_at <= ?)
          GROUP BY grp
          ORDER BY revenue_cents DESC
          """;
      case "product" ->
          """
          SELECT p.name AS grp,
                 COUNT(DISTINCT o.id) AS orders_count,
                 COALESCE(SUM(oi.quantity), 0) AS units_sold,
                 COALESCE(SUM(oi.line_total_cents), 0) AS revenue_cents
          FROM orders o
          JOIN order_items oi ON o.id = oi.order_id
          JOIN products p ON oi.product_id = p.id
          WHERE o.status NOT IN ('CANCELLED')
            AND (cast(? as timestamptz) IS NULL OR o.placed_at >= ?)
            AND (cast(? as timestamptz) IS NULL OR o.placed_at <= ?)
          GROUP BY grp
          ORDER BY revenue_cents DESC
          """;
      case "paymentmethod" ->
          """
          SELECT o.payment_method AS grp,
                 COUNT(DISTINCT o.id) AS orders_count,
                 COALESCE(SUM(oi.quantity), 0) AS units_sold,
                 COALESCE(SUM(oi.line_total_cents), 0) AS revenue_cents
          FROM orders o
          JOIN order_items oi ON o.id = oi.order_id
          WHERE o.status NOT IN ('CANCELLED')
            AND (cast(? as timestamptz) IS NULL OR o.placed_at >= ?)
            AND (cast(? as timestamptz) IS NULL OR o.placed_at <= ?)
          GROUP BY grp
          ORDER BY revenue_cents DESC
          """;
      default ->
          """
          SELECT TO_CHAR(date_trunc('day', o.placed_at AT TIME ZONE 'Asia/Phnom_Penh'), 'YYYY-MM-DD') AS grp,
                 COUNT(DISTINCT o.id) AS orders_count,
                 COALESCE(SUM(oi.quantity), 0) AS units_sold,
                 COALESCE(SUM(oi.line_total_cents), 0) AS revenue_cents
          FROM orders o
          JOIN order_items oi ON o.id = oi.order_id
          WHERE o.status NOT IN ('CANCELLED')
            AND (cast(? as timestamptz) IS NULL OR o.placed_at >= ?)
            AND (cast(? as timestamptz) IS NULL OR o.placed_at <= ?)
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
