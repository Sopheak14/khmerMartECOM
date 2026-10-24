package com.khmermart.controller.admin;

import com.khmermart.aspect.Audited;
import com.khmermart.common.response.PaginationResponse;
import com.khmermart.common.response.SuccessResponse;
import com.khmermart.model.enumerate.OrderStatus;
import com.khmermart.model.enumerate.PaymentStatus;
import com.khmermart.model.request.AddTrackingEventRequest;
import com.khmermart.model.request.ShipOrderRequest;
import com.khmermart.model.request.UpdateOrderStatusRequest;
import com.khmermart.model.response.OrderDto;
import com.khmermart.model.response.TrackingEventDto;
import com.khmermart.security.CurrentUser;
import com.khmermart.security.UserPrincipal;
import com.khmermart.service.OrderService;
import com.khmermart.service.TrackingService;
import com.khmermart.util.ApiResponseUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/orders")
@Tag(name = "Admin Orders", description = "Admin order management")
public class AdminOrderController {

  private final OrderService orderService;
  private final TrackingService trackingService;

  public AdminOrderController(OrderService orderService, TrackingService trackingService) {
    this.orderService = orderService;
    this.trackingService = trackingService;
  }

  @GetMapping
  @PreAuthorize("hasAnyRole('STAFF', 'ADMIN')")
  @Operation(summary = "Search and filter orders with pagination")
  public SuccessResponse<List<OrderDto>> getOrders(
      @RequestParam(required = false) OrderStatus status,
      @RequestParam(required = false) PaymentStatus paymentStatus,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          Instant from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          Instant to,
      @RequestParam(required = false) String q,
      @PageableDefault(size = 20, sort = "placedAt", direction = Sort.Direction.DESC)
          Pageable pageable) {
    Page<OrderDto> page = orderService.getAdminOrders(status, paymentStatus, from, to, q, pageable);
    PaginationResponse meta =
        new PaginationResponse(
            page.getNumber() + 1, page.getSize(), page.getTotalElements(), page.getTotalPages());
    return ApiResponseUtil.success(page.getContent(), meta);
  }

  @GetMapping("/{orderNumber}")
  @PreAuthorize("hasAnyRole('STAFF', 'ADMIN')")
  @Operation(summary = "Get order detail with customer and timeline")
  public SuccessResponse<OrderDto> getOrderDetail(@PathVariable String orderNumber) {
    OrderDto dto = orderService.getOrderByNumber(null, orderNumber, true);
    return ApiResponseUtil.success(dto);
  }

  @PostMapping("/{orderNumber}/ship")
  @PreAuthorize("hasAnyRole('STAFF', 'ADMIN')")
  @Audited(action = "SHIP_ORDER", entity = "ORDER")
  @Operation(summary = "Mark order as SHIPPED and generate tracking number")
  public SuccessResponse<OrderDto> shipOrder(
      @PathVariable String orderNumber, @RequestBody(required = false) ShipOrderRequest request) {
    String carrier = request != null ? request.carrier() : "KhmerMart Express";
    OrderDto dto = orderService.shipOrder(orderNumber, carrier);
    return ApiResponseUtil.success(dto);
  }

  @PostMapping("/{orderNumber}/tracking-events")
  @PreAuthorize("hasAnyRole('STAFF', 'ADMIN')")
  @Audited(action = "ADD_TRACKING_EVENT", entity = "ORDER")
  @Operation(summary = "Add tracking event to order shipment")
  public SuccessResponse<TrackingEventDto> addTrackingEvent(
      @PathVariable String orderNumber, @Valid @RequestBody AddTrackingEventRequest request) {
    TrackingEventDto dto = trackingService.addTrackingEvent(orderNumber, request);
    return ApiResponseUtil.success(dto);
  }

  @PatchMapping("/{orderNumber}/status")
  @PreAuthorize("hasAnyRole('STAFF', 'ADMIN')")
  @Audited(action = "UPDATE_ORDER_STATUS", entity = "ORDER")
  @Operation(summary = "Update order status")
  public SuccessResponse<OrderDto> updateOrderStatus(
      @CurrentUser UserPrincipal principal,
      @PathVariable String orderNumber,
      @Valid @RequestBody UpdateOrderStatusRequest request) {
    OrderDto dto =
        orderService.updateStatus(
            orderNumber,
            request.status(),
            request.note(),
            principal != null ? principal.id() : null);
    return ApiResponseUtil.success(dto);
  }
}
