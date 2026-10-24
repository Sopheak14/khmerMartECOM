package com.khmermart.controller.admin;

import com.khmermart.common.response.SuccessResponse;
import com.khmermart.model.response.SalesGroupDto;
import com.khmermart.service.ReportService;
import com.khmermart.util.ApiResponseUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/reports")
@Tag(name = "Admin Reports", description = "Financial and sales performance analytics")
public class AdminReportController {

  private final ReportService reportService;

  public AdminReportController(ReportService reportService) {
    this.reportService = reportService;
  }

  @GetMapping("/sales")
  @PreAuthorize("hasRole('ADMIN')")
  @Operation(summary = "Get grouped sales report (by day, month, category, product, paymentMethod)")
  public SuccessResponse<List<SalesGroupDto>> getSalesReport(
      @RequestParam(required = false, defaultValue = "day") String groupBy,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          Instant from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          Instant to) {
    List<SalesGroupDto> report = reportService.getSalesReport(groupBy, from, to);
    return ApiResponseUtil.success(report);
  }

  @GetMapping(value = "/sales.csv", produces = "text/csv")
  @PreAuthorize("hasRole('ADMIN')")
  @Operation(summary = "Export sales report as CSV")
  public ResponseEntity<byte[]> exportSalesCsv(
      @RequestParam(required = false, defaultValue = "day") String groupBy,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          Instant from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          Instant to) {
    byte[] data = reportService.exportSalesCsv(groupBy, from, to);
    return ResponseEntity.ok()
        .header(
            HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"sales-" + groupBy + ".csv\"")
        .contentType(MediaType.parseMediaType("text/csv"))
        .body(data);
  }

  @GetMapping(
      value = "/sales.xlsx",
      produces = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
  @PreAuthorize("hasRole('ADMIN')")
  @Operation(summary = "Export sales report as Excel (XLSX)")
  public ResponseEntity<byte[]> exportSalesXlsx(
      @RequestParam(required = false, defaultValue = "day") String groupBy,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          Instant from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          Instant to) {
    byte[] data = reportService.exportSalesXlsx(groupBy, from, to);
    return ResponseEntity.ok()
        .header(
            HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"sales-" + groupBy + ".xlsx\"")
        .contentType(
            MediaType.parseMediaType(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
        .body(data);
  }
}
