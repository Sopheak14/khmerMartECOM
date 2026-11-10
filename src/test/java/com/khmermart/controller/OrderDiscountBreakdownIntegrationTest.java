package com.khmermart.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.khmermart.AbstractIntegrationTest;
import com.khmermart.model.entity.Address;
import com.khmermart.model.entity.Order;
import com.khmermart.model.enumerate.OrderStatus;
import com.khmermart.model.enumerate.PaymentMethod;
import com.khmermart.model.enumerate.PaymentStatus;
import com.khmermart.model.request.ApplyCouponRequest;
import com.khmermart.model.request.CartItemRequest;
import com.khmermart.model.request.CheckoutRequest;
import com.khmermart.repository.AddressRepository;
import com.khmermart.repository.OrderRepository;
import com.khmermart.seed.SeedCatalog;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
class OrderDiscountBreakdownIntegrationTest extends AbstractIntegrationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private AddressRepository addressRepository;
  @Autowired private OrderRepository orderRepository;

  private String login(String email, String password) throws Exception {
    String res =
        mockMvc
            .perform(
                post("/api/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsString(
                            Map.of("email", email, "password", password))))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return "Bearer " + objectMapper.readTree(res).path("data").path("accessToken").asText();
  }

  @Test
  @DisplayName(
      "OrderDTO.discountBreakdown: demo order (flash 2000 + SAVE5 500) on checkout, customer and admin endpoints; legacy order without snapshot")
  void testDiscountBreakdownOnCheckoutCustomerAndAdmin() throws Exception {
    long flash = SeedCatalog.DEMO_ORDER_FLASH_DISCOUNT_CENTS;
    long save5 = SeedCatalog.SAVE5_CENTS;

    String email = "breakdown+" + UUID.randomUUID() + "@khmermart.local";
    String registerRes =
        mockMvc
            .perform(
                post("/api/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsString(
                            Map.of(
                                "email", email,
                                "password", "Breakdown@12345",
                                "fullName", "Breakdown Tester",
                                "phone", "+85512345678"))))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    markVerified(email);
    String customerToken = login(email, "Breakdown@12345");
    long customerId = userId(email);
    String adminToken = login("admin@khmermart.local", "Admin@12345");

    Address address =
        addressRepository.save(
            Address.builder()
                .userId(customerId)
                .label("Home")
                .recipientName("Breakdown Tester")
                .phone("+85512345678")
                .line1("Street 2004")
                .district("Tuol Kouk")
                .province("Phnom Penh")
                .isDefault(true)
                .build());

    // Demo order: hero Ruby Red Standard, flash sale + SAVE5
    mockMvc
        .perform(
            post("/api/cart/items")
                .header("Authorization", customerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new CartItemRequest(
                            SeedCatalog.HERO_ID, SeedCatalog.HERO_RED_STANDARD, 1))))
        .andExpect(status().isOk());
    mockMvc
        .perform(
            post("/api/cart/coupon")
                .header("Authorization", customerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new ApplyCouponRequest(SeedCatalog.COUPON_SAVE5))))
        .andExpect(status().isOk());

    String checkoutRes =
        mockMvc
            .perform(
                post("/api/checkout")
                    .header("Authorization", customerToken)
                    .header("Idempotency-Key", UUID.randomUUID().toString())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsString(
                            new CheckoutRequest(
                                address.getId(), "STANDARD", PaymentMethod.KHQR, null))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.order.discountCents").value(flash + save5))
            // checkout response itself carries the snapshot
            .andExpect(jsonPath("$.data.order.discountBreakdown.promotions.length()").value(1))
            .andExpect(
                jsonPath("$.data.order.discountBreakdown.promotions[0].label")
                    .value("Flash Sale Discount"))
            .andExpect(
                jsonPath("$.data.order.discountBreakdown.promotions[0].amountCents").value(flash))
            .andExpect(
                jsonPath("$.data.order.discountBreakdown.coupon.code")
                    .value(SeedCatalog.COUPON_SAVE5))
            .andExpect(jsonPath("$.data.order.discountBreakdown.coupon.amountCents").value(save5))
            .andReturn()
            .getResponse()
            .getContentAsString();
    String orderNumber =
        objectMapper.readTree(checkoutRes).path("data").path("order").path("orderNumber").asText();

    // Customer endpoint: discountCents == sum(promotions) + coupon, from the snapshot
    mockMvc
        .perform(get("/api/orders/" + orderNumber).header("Authorization", customerToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.discountCents").value(flash + save5))
        .andExpect(
            jsonPath("$.data.discountBreakdown.promotions[0].label").value("Flash Sale Discount"))
        .andExpect(jsonPath("$.data.discountBreakdown.promotions[0].amountCents").value(flash))
        .andExpect(jsonPath("$.data.discountBreakdown.coupon.code").value(SeedCatalog.COUPON_SAVE5))
        .andExpect(jsonPath("$.data.discountBreakdown.coupon.amountCents").value(save5));

    // Admin endpoint
    mockMvc
        .perform(get("/api/admin/orders/" + orderNumber).header("Authorization", adminToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.discountBreakdown.promotions[0].amountCents").value(flash))
        .andExpect(jsonPath("$.data.discountBreakdown.coupon.code").value(SeedCatalog.COUPON_SAVE5))
        .andExpect(jsonPath("$.data.discountBreakdown.coupon.amountCents").value(save5));

    // Snapshot, not recalculation: the stored value is what the API returns after the coupon
    // changes
    Order stored = orderRepository.findByOrderNumber(orderNumber).orElseThrow();
    stored.setDiscountBreakdown(
        "{\"promotions\":[{\"label\":\"Snapshot Only\",\"amountCents\":1}],\"coupon\":null}");
    orderRepository.save(stored);
    mockMvc
        .perform(get("/api/orders/" + orderNumber).header("Authorization", customerToken))
        .andExpect(jsonPath("$.data.discountBreakdown.promotions[0].label").value("Snapshot Only"))
        .andExpect(
            jsonPath("$.data.discountBreakdown.coupon").value(org.hamcrest.Matchers.nullValue()));

    // Release the reserved hero stock
    mockMvc
        .perform(
            post("/api/orders/" + orderNumber + "/cancel")
                .header("Authorization", customerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"test cleanup\"}"))
        .andExpect(status().isOk());

    // Legacy order with no snapshot: promotions [] and coupon null
    Order legacy =
        orderRepository.save(
            Order.builder()
                .orderNumber("KM-TEST-LEGACY-" + System.nanoTime())
                .userId(customerId)
                .status(OrderStatus.CONFIRMED)
                .paymentStatus(PaymentStatus.PAID)
                .paymentMethod(PaymentMethod.KHQR)
                .subtotalCents(1000L)
                .discountCents(100L)
                .shippingCents(0L)
                .totalCents(900L)
                .shippingAddress("{}")
                .shippingMethodCode("STANDARD")
                .placedAt(Instant.now())
                .build());
    for (String[] who :
        new String[][] {{"/api/orders/", customerToken}, {"/api/admin/orders/", adminToken}}) {
      mockMvc
          .perform(get(who[0] + legacy.getOrderNumber()).header("Authorization", who[1]))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.data.discountBreakdown.promotions.length()").value(0))
          .andExpect(
              jsonPath("$.data.discountBreakdown.coupon").value(org.hamcrest.Matchers.nullValue()));
    }
  }
}
