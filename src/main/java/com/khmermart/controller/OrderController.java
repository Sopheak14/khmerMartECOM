package com.khmermart.controller;

import com.khmermart.common.response.SuccessResponse;
import com.khmermart.model.enumerate.OrderStatus;
import com.khmermart.model.enumerate.Roles;
import com.khmermart.model.request.CancelOrderRequest;
import com.khmermart.model.response.CartDto;
import com.khmermart.model.response.OrderDto;
import com.khmermart.security.CurrentUser;
import com.khmermart.security.UserPrincipal;
import com.khmermart.service.InvoiceService;
import com.khmermart.service.OrderService;
import com.khmermart.util.ApiResponseUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/orders")
@Tag(name = "Orders", description = "Customer order management and history")
public class OrderController {

  private final OrderService orderService;
  private final InvoiceService invoiceService;

  public OrderController(OrderService orderService, InvoiceService invoiceService) {
    this.orderService = orderService;
    this.invoiceService = invoiceService;
  }

  @GetMapping
  @Operation(summary = "List customer orders (paginated, filter by status)")
  public SuccessResponse<List<OrderDto>> getOrders(
      @CurrentUser UserPrincipal principal,
      @RequestParam(required = false) OrderStatus status,
      @PageableDefault(page = 0, size = 10, sort = "placedAt", direction = Sort.Direction.DESC)
          Pageable pageable) {
    return orderService.getOrders(principal.id(), status, pageable);
  }

  @GetMapping("/{orderNumber}")
  @Operation(summary = "Get order details by order number")
  public SuccessResponse<OrderDto> getOrderByNumber(
      @CurrentUser UserPrincipal principal, @PathVariable String orderNumber) {
    boolean isStaffOrAdmin =
        principal != null && (principal.role() == Roles.ADMIN || principal.role() == Roles.STAFF);
    OrderDto dto = orderService.getOrderByNumber(principal.id(), orderNumber, isStaffOrAdmin);
    return ApiResponseUtil.success(dto);
  }

  @PostMapping("/{orderNumber}/cancel")
  @Operation(summary = "Cancel an order before shipping")
  public SuccessResponse<OrderDto> cancelOrder(
      @CurrentUser UserPrincipal principal,
      @PathVariable String orderNumber,
      @RequestBody(required = false) CancelOrderRequest request) {
    String reason = request != null ? request.reason() : "Cancelled by customer";
    OrderDto dto = orderService.cancelOrder(principal.id(), orderNumber, reason);
    return ApiResponseUtil.success(dto);
  }

  @PostMapping("/{orderNumber}/reorder")
  @Operation(summary = "Re-add active items from an order into the cart")
  public SuccessResponse<CartDto> reorder(
      @CurrentUser UserPrincipal principal, @PathVariable String orderNumber) {
    CartDto cart = orderService.reorder(principal.id(), orderNumber);
    return ApiResponseUtil.success(cart);
  }

  @GetMapping("/{orderNumber}/invoice.pdf")
  @Operation(summary = "Download PDF invoice for an order")
  public ResponseEntity<byte[]> getInvoice(
      @CurrentUser UserPrincipal principal, @PathVariable String orderNumber) {
    boolean isStaffOrAdmin =
        principal != null && (principal.role() == Roles.ADMIN || principal.role() == Roles.STAFF);
    // Verifies ownership / permissions
    orderService.getOrderByNumber(principal.id(), orderNumber, isStaffOrAdmin);

    byte[] pdfBytes = invoiceService.generateInvoicePdf(orderNumber);

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_PDF);
    headers.setContentDisposition(
        ContentDisposition.attachment().filename("invoice-" + orderNumber + ".pdf").build());
    headers.setContentLength(pdfBytes.length);

    return ResponseEntity.ok().headers(headers).body(pdfBytes);
  }
}
