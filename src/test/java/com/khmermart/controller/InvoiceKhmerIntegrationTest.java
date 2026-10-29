package com.khmermart.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.khmermart.AbstractIntegrationTest;
import com.khmermart.model.entity.Inventory;
import com.khmermart.model.entity.Order;
import com.khmermart.model.entity.OrderItem;
import com.khmermart.model.entity.Payment;
import com.khmermart.model.entity.Product;
import com.khmermart.model.enumerate.OrderStatus;
import com.khmermart.model.enumerate.PaymentMethod;
import com.khmermart.model.enumerate.PaymentStatus;
import com.khmermart.model.enumerate.Roles;
import com.khmermart.repository.InventoryRepository;
import com.khmermart.repository.OrderItemRepository;
import com.khmermart.repository.OrderRepository;
import com.khmermart.repository.PaymentRepository;
import com.khmermart.repository.ProductRepository;
import com.khmermart.security.JwtService;
import com.khmermart.seed.SeedCatalog;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
public class InvoiceKhmerIntegrationTest extends AbstractIntegrationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private OrderRepository orderRepository;
  @Autowired private OrderItemRepository orderItemRepository;
  @Autowired private PaymentRepository paymentRepository;
  @Autowired private ProductRepository productRepository;
  @Autowired private InventoryRepository inventoryRepository;
  @Autowired private JwtService jwtService;

  private String customerToken;

  @BeforeEach
  void setUp() {
    customerToken =
        "Bearer " + jwtService.generateAccessToken(3L, "customer1@khmermart.local", Roles.CUSTOMER);
    new File("docs").mkdirs();
  }

  @Test
  @DisplayName(
      "Khmer invoice PDF: embedded Khmer font, pdftotext extraction, visual PNG, and paid sample")
  void testKhmerInvoiceGeneration() throws Exception {
    // The hero product, Ruby Red Standard variant, bought on flash sale with the SAVE5 coupon
    SeedCatalog.Product hero = SeedCatalog.product(SeedCatalog.HERO_ID);
    SeedCatalog.Variant heroVariant = SeedCatalog.variant(SeedCatalog.HERO_RED_STANDARD);
    long subtotal = SeedCatalog.HERO_STANDARD_CENTS;
    long flashDiscount = SeedCatalog.DEMO_ORDER_FLASH_DISCOUNT_CENTS;
    long total = SeedCatalog.DEMO_ORDER_TOTAL_CENTS;

    Product product =
        productRepository.save(
            Product.builder()
                .categoryId(SeedCatalog.CAT_SILK)
                .name(hero.name())
                .nameKm(hero.nameKm())
                .slug(hero.slug() + "-" + UUID.randomUUID())
                .sku(heroVariant.sku())
                .priceCents(subtotal)
                .active(true)
                .build());

    String discountBreakdown =
        """
        {
          "promotions": [
            {"label": "Flash Sale Discount", "amountCents": %d}
          ],
          "coupon": {
            "code": "%s",
            "amountCents": %d
          }
        }
        """
            .formatted(flashDiscount, SeedCatalog.COUPON_SAVE5, SeedCatalog.SAVE5_CENTS);

    Order order =
        orderRepository.save(
            Order.builder()
                .orderNumber("KM-20261025-KHMER01")
                .userId(3L)
                .status(OrderStatus.CONFIRMED)
                .paymentStatus(PaymentStatus.PAID)
                .paymentMethod(PaymentMethod.KHQR)
                .subtotalCents(subtotal)
                .discountCents(flashDiscount + SeedCatalog.SAVE5_CENTS)
                .shippingCents(0L)
                .totalCents(total)
                .couponCode(SeedCatalog.COUPON_SAVE5)
                .discountBreakdown(discountBreakdown)
                .shippingAddress(
                    "{\"recipientName\":\"សុភាព\",\"phone\":\"+85512345678\",\"line1\":\"ផ្លូវ ២០០៤ ខណ្ឌពោធិ៍សែនជ័យ\",\"province\":\"រាជធានីភ្នំពេញ\"}")
                .shippingMethodCode("STANDARD")
                .placedAt(Instant.parse("2026-10-23T08:51:43Z"))
                .confirmedAt(Instant.parse("2026-10-23T08:52:28Z"))
                .build());

    orderItemRepository.save(
        OrderItem.builder()
            .orderId(order.getId())
            .productId(product.getId())
            .productNameSnapshot(hero.name())
            .skuSnapshot(heroVariant.sku())
            .unitPriceCents(subtotal)
            .quantity(1)
            .lineTotalCents(subtotal)
            .build());

    paymentRepository.save(
        Payment.builder()
            .orderId(order.getId())
            .method(PaymentMethod.KHQR)
            .status(PaymentStatus.PAID)
            .amountCents(total)
            .currency("USD")
            .bakongHash("MOCK-2874fccc-513a-4c0e-bc77-516d29419d69")
            .paidAt(Instant.parse("2026-10-23T08:52:28Z"))
            .createdAt(Instant.parse("2026-10-23T08:51:43Z"))
            .build());

    byte[] pdfBytes =
        mockMvc
            .perform(
                get("/api/orders/{orderNumber}/invoice.pdf", order.getOrderNumber())
                    .header("Authorization", customerToken))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsByteArray();

    new File("target").mkdirs();
    File paidPdfFile = new File("target/test-invoice-paid.pdf");
    try (FileOutputStream fos = new FileOutputStream(paidPdfFile)) {
      fos.write(pdfBytes);
    }

    // Verify pdffonts: Noto Sans Khmer must be embedded (emb = yes)
    Process fontsProc = new ProcessBuilder("pdffonts", paidPdfFile.getAbsolutePath()).start();
    String fontsOutput;
    try (BufferedReader reader =
        new BufferedReader(new InputStreamReader(fontsProc.getInputStream()))) {
      StringBuilder sb = new StringBuilder();
      String line;
      while ((line = reader.readLine()) != null) {
        sb.append(line).append("\n");
      }
      fontsOutput = sb.toString();
    }
    fontsProc.waitFor();
    System.out.println("=== RAW PDFFONTS OUTPUT ===");
    System.out.println(fontsOutput);

    assertThat(fontsOutput).contains("NotoSansKhmer");
    assertThat(fontsOutput).contains("yes");

    // Verify pdftotext: exact Khmer strings extracted
    Process textProc = new ProcessBuilder("pdftotext", paidPdfFile.getAbsolutePath(), "-").start();
    String textOutput;
    try (BufferedReader reader =
        new BufferedReader(new InputStreamReader(textProc.getInputStream()))) {
      StringBuilder sb = new StringBuilder();
      String line;
      while ((line = reader.readLine()) != null) {
        sb.append(line).append("\n");
      }
      textOutput = sb.toString();
    }
    textProc.waitFor();
    System.out.println("=== RAW PDFTOTEXT OUTPUT ===");
    System.out.println(textOutput);

    assertThat(textOutput).contains("សុភាព");
    assertThat(textOutput).contains("ផ្លូវ ២០០៤ ខណ្ឌពោធិ៍សែនជ័យ");
    assertThat(textOutput).contains(hero.nameKm());
    assertThat(textOutput).contains("៛");
    assertThat(textOutput).contains("KhmerMart");
    assertThat(textOutput).contains("Payment in MOCK mode — not a real transaction");
    assertThat(textOutput).contains("Flash Sale Discount");
    assertThat(textOutput).contains("Coupon (" + SeedCatalog.COUPON_SAVE5 + ")");
    assertThat(textOutput).contains("(ICT)");

    // Render page 1 to PNG via pdftoppm -png -r 100
    Process ppmProc =
        new ProcessBuilder(
                "pdftoppm",
                "-png",
                "-r",
                "100",
                paidPdfFile.getAbsolutePath(),
                "target/test-invoice-khmer-tmp")
            .start();
    ppmProc.waitFor();

    File generatedPng = new File("target/test-invoice-khmer-tmp-1.png");
    assertThat(generatedPng).exists();
  }

  @Test
  @DisplayName("Cancelled + Refunded invoice PDF: shows CANCELLED stamp and Refunded line")
  void testCancelledRefundedInvoice() throws Exception {
    Product product =
        productRepository.save(
            Product.builder()
                .categoryId(1L)
                .name("Silk Scarf")
                .nameKm("ក្រមាសូត្រ")
                .slug("silk-scarf-" + UUID.randomUUID())
                .sku("SILK-SCF-01")
                .priceCents(5000L)
                .active(true)
                .build());

    inventoryRepository.save(
        Inventory.builder()
            .productId(product.getId())
            .quantityOnHand(10)
            .quantityReserved(0)
            .lowStockThreshold(2)
            .build());

    Order order =
        orderRepository.save(
            Order.builder()
                .orderNumber("KM-20261025-REFUND01")
                .userId(3L)
                .status(OrderStatus.CONFIRMED)
                .paymentStatus(PaymentStatus.PAID)
                .paymentMethod(PaymentMethod.KHQR)
                .subtotalCents(5000L)
                .discountCents(0L)
                .shippingCents(0L)
                .totalCents(5000L)
                .shippingAddress(
                    "{\"recipientName\":\"សុភាព\",\"phone\":\"+85512345678\",\"line1\":\"Street 2004\",\"province\":\"Phnom Penh\"}")
                .shippingMethodCode("STANDARD")
                .placedAt(Instant.now())
                .build());

    orderItemRepository.save(
        OrderItem.builder()
            .orderId(order.getId())
            .productId(product.getId())
            .productNameSnapshot("Silk Scarf")
            .skuSnapshot("SILK-SCF-01")
            .unitPriceCents(5000L)
            .quantity(1)
            .lineTotalCents(5000L)
            .build());

    paymentRepository.save(
        Payment.builder()
            .orderId(order.getId())
            .method(PaymentMethod.KHQR)
            .status(PaymentStatus.PAID)
            .amountCents(5000L)
            .currency("USD")
            .bakongHash("MOCK-REFUND-HASH-999")
            .paidAt(Instant.now())
            .createdAt(Instant.now())
            .build());

    // Cancel order via API (paid -> cancel flow triggers refund & status REFUNDED)
    mockMvc
        .perform(
            post("/api/orders/{orderNumber}/cancel", order.getOrderNumber())
                .header("Authorization", customerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"Damaged product\"}"))
        .andExpect(status().isOk());

    byte[] pdfBytes =
        mockMvc
            .perform(
                get("/api/orders/{orderNumber}/invoice.pdf", order.getOrderNumber())
                    .header("Authorization", customerToken))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsByteArray();

    File refundPdfFile = new File("target/test-invoice-refunded.pdf");
    try (FileOutputStream fos = new FileOutputStream(refundPdfFile)) {
      fos.write(pdfBytes);
    }
    assertThat(refundPdfFile).exists();

    // Verify CANCELLED stamp and Refunded line in pdftotext
    Process textProc =
        new ProcessBuilder("pdftotext", refundPdfFile.getAbsolutePath(), "-").start();
    String textOutput;
    try (BufferedReader reader =
        new BufferedReader(new InputStreamReader(textProc.getInputStream()))) {
      StringBuilder sb = new StringBuilder();
      String line;
      while ((line = reader.readLine()) != null) {
        sb.append(line).append("\n");
      }
      textOutput = sb.toString();
    }
    textProc.waitFor();

    System.out.println("=== REFUNDED INVOICE PDFTOTEXT ===");
    System.out.println(textOutput);

    assertThat(textOutput).contains("CANCELLED");
    assertThat(textOutput).contains("Order Status: CANCELLED");
    assertThat(textOutput).contains("Payment Status: REFUNDED");
    assertThat(textOutput).contains("Refunded: $50.00 on");
    assertThat(textOutput).contains("(ICT)");
  }
}
