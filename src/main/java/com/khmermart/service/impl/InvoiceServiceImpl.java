package com.khmermart.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.khmermart.common.exception.CustomException;
import com.khmermart.common.exception.ErrorCode;
import com.khmermart.config.AppProperty;
import com.khmermart.model.entity.AppUser;
import com.khmermart.model.entity.Order;
import com.khmermart.model.entity.OrderItem;
import com.khmermart.model.entity.Payment;
import com.khmermart.model.entity.Product;
import com.khmermart.model.entity.Refund;
import com.khmermart.model.enumerate.OrderStatus;
import com.khmermart.model.enumerate.PaymentStatus;
import com.khmermart.model.response.AddressDto;
import com.khmermart.repository.AppUserRepository;
import com.khmermart.repository.OrderItemRepository;
import com.khmermart.repository.OrderRepository;
import com.khmermart.repository.PaymentRepository;
import com.khmermart.repository.ProductRepository;
import com.khmermart.repository.RefundRepository;
import com.khmermart.service.InvoiceService;
import com.lowagie.text.Chunk;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.LayoutProcessor;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import jakarta.annotation.PostConstruct;
import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URL;
import java.text.DecimalFormat;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
public class InvoiceServiceImpl implements InvoiceService {

  private final OrderRepository orderRepository;
  private final OrderItemRepository orderItemRepository;
  private final PaymentRepository paymentRepository;
  private final RefundRepository refundRepository;
  private final ProductRepository productRepository;
  private final AppUserRepository userRepository;
  private final AppProperty appProperty;
  private final ObjectMapper objectMapper;

  private static final DateTimeFormatter DATE_FORMATTER =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss '(ICT)'")
          .withZone(ZoneId.of("Asia/Phnom_Penh"));

  private static final DecimalFormat MONEY_FORMAT = new DecimalFormat("$#,##0.00");
  private static final DecimalFormat KHR_FORMAT = new DecimalFormat("#,##0");

  private byte[] latinRegularBytes;
  private byte[] latinBoldBytes;
  private byte[] khmerRegularBytes;
  private byte[] khmerBoldBytes;
  private String latinRegularUrl;
  private String latinBoldUrl;
  private String khmerRegularUrl;
  private String khmerBoldUrl;

  public InvoiceServiceImpl(
      OrderRepository orderRepository,
      OrderItemRepository orderItemRepository,
      PaymentRepository paymentRepository,
      RefundRepository refundRepository,
      ProductRepository productRepository,
      AppUserRepository userRepository,
      AppProperty appProperty,
      ObjectMapper objectMapper) {
    this.orderRepository = orderRepository;
    this.orderItemRepository = orderItemRepository;
    this.paymentRepository = paymentRepository;
    this.refundRepository = refundRepository;
    this.productRepository = productRepository;
    this.userRepository = userRepository;
    this.appProperty = appProperty;
    this.objectMapper = objectMapper;
  }

  @PostConstruct
  public synchronized void init() {
    LayoutProcessor.enable();
    LayoutProcessor.setWriteActualText();
    loadFontResources();
  }

  private void loadFontResources() {
    try {
      URL lrUrl = getClass().getResource("/fonts/NotoSans-Regular.ttf");
      if (lrUrl != null) {
        latinRegularUrl = lrUrl.toExternalForm();
        try (InputStream is = lrUrl.openStream()) {
          latinRegularBytes = is.readAllBytes();
        }
      }

      URL lbUrl = getClass().getResource("/fonts/NotoSans-Bold.ttf");
      if (lbUrl != null) {
        latinBoldUrl = lbUrl.toExternalForm();
        try (InputStream is = lbUrl.openStream()) {
          latinBoldBytes = is.readAllBytes();
        }
      }

      URL krUrl = getClass().getResource("/fonts/NotoSansKhmer-Regular.ttf");
      if (krUrl != null) {
        khmerRegularUrl = krUrl.toExternalForm();
        try (InputStream is = krUrl.openStream()) {
          khmerRegularBytes = is.readAllBytes();
        }
      }

      URL kbUrl = getClass().getResource("/fonts/NotoSansKhmer-Bold.ttf");
      if (kbUrl != null) {
        khmerBoldUrl = kbUrl.toExternalForm();
        try (InputStream is = kbUrl.openStream()) {
          khmerBoldBytes = is.readAllBytes();
        }
      }
    } catch (Exception e) {
      throw new IllegalStateException("Failed to load embedded font resources", e);
    }
  }

  @Override
  @Transactional(readOnly = true)
  public byte[] generateInvoicePdf(String orderNumber) {
    Order order =
        orderRepository
            .findByOrderNumber(orderNumber)
            .orElseThrow(() -> new CustomException(ErrorCode.NOT_FOUND, "Order not found"));

    List<OrderItem> items = orderItemRepository.findByOrderId(order.getId());
    Payment payment = paymentRepository.findFirstByOrderIdOrderByIdDesc(order.getId()).orElse(null);
    List<Refund> refunds =
        payment != null ? refundRepository.findByPaymentId(payment.getId()) : Collections.emptyList();
    Refund latestRefund =
        refunds.stream().max(Comparator.comparing(Refund::getCreatedAt)).orElse(null);
    AppUser user = userRepository.findById(order.getUserId()).orElse(null);

    AddressDto address = null;
    if (order.getShippingAddress() != null) {
      try {
        address = objectMapper.readValue(order.getShippingAddress(), AddressDto.class);
      } catch (Exception ignored) {
      }
    }

    Document document = new Document(PageSize.A4, 36, 36, 36, 36);
    ByteArrayOutputStream baos = new ByteArrayOutputStream();

    try {
      PdfWriter.getInstance(document, baos);
      document.open();

      // Ensure LayoutProcessor is enabled
      LayoutProcessor.enable();
      LayoutProcessor.setWriteActualText();

      // Setup fonts
      BaseFont latinBase =
          BaseFont.createFont(latinRegularUrl, BaseFont.IDENTITY_H, BaseFont.EMBEDDED, true, latinRegularBytes, null);
      BaseFont latinBoldBase =
          BaseFont.createFont(latinBoldUrl, BaseFont.IDENTITY_H, BaseFont.EMBEDDED, true, latinBoldBytes, null);
      BaseFont khmerBase =
          BaseFont.createFont(khmerRegularUrl, BaseFont.IDENTITY_H, BaseFont.EMBEDDED, true, khmerRegularBytes, null);
      BaseFont khmerBoldBase =
          BaseFont.createFont(khmerBoldUrl, BaseFont.IDENTITY_H, BaseFont.EMBEDDED, true, khmerBoldBytes, null);

      Font titleFont = new Font(latinBoldBase, 20, Font.NORMAL, new Color(30, 41, 59));
      Font subTitleFont = new Font(latinBase, 10, Font.NORMAL, Color.DARK_GRAY);
      Font headerFont = new Font(latinBoldBase, 12, Font.NORMAL, Color.BLACK);
      Font boldFont = new Font(latinBoldBase, 10, Font.NORMAL, Color.BLACK);
      Font normalFont = new Font(latinBase, 10, Font.NORMAL, Color.BLACK);
      Font khmerNormalFont = new Font(khmerBase, 10, Font.NORMAL, Color.BLACK);
      Font khmerBoldFont = new Font(khmerBoldBase, 10, Font.NORMAL, Color.BLACK);
      Font redStampFont = new Font(latinBoldBase, 16, Font.NORMAL, new Color(220, 38, 38));
      Font smallGrayFont = new Font(latinBase, 9, Font.NORMAL, Color.GRAY);

      // 1. Header Table (Seller Block on Left, Invoice Info on Right)
      PdfPTable headerTable = new PdfPTable(2);
      headerTable.setWidthPercentage(100);
      headerTable.setWidths(new float[] {1.1f, 1.0f});

      // Left Column: Seller Block
      PdfPCell sellerCell = new PdfPCell();
      sellerCell.setBorder(PdfPCell.NO_BORDER);
      sellerCell.setPadding(0f);

      Paragraph sellerTitle = new Paragraph("KhmerMart", titleFont);
      sellerCell.addElement(sellerTitle);

      Paragraph sellerDetails =
          new Paragraph(
              "KhmerMart Online Store\n"
                  + "#123 Russian Federation Blvd, Phnom Penh, Cambodia\n"
                  + "Phone: +855 12 345 678\n"
                  + "Email: support@khmermart.com",
              subTitleFont);
      sellerDetails.setSpacingBefore(4);
      sellerCell.addElement(sellerDetails);
      headerTable.addCell(sellerCell);

      // Right Column: Invoice & Order Status Info
      PdfPCell invoiceMetaCell = new PdfPCell();
      invoiceMetaCell.setBorder(PdfPCell.NO_BORDER);
      invoiceMetaCell.setPadding(0f);
      invoiceMetaCell.setHorizontalAlignment(Element.ALIGN_RIGHT);

      Paragraph invoiceHeader = new Paragraph("OFFICIAL INVOICE", headerFont);
      invoiceHeader.setAlignment(Element.ALIGN_RIGHT);
      invoiceMetaCell.addElement(invoiceHeader);

      // Resolve latest payment status
      String paymentStatus;
      if (order.getPaymentStatus() == PaymentStatus.REFUNDED
          || (payment != null && payment.getStatus() == PaymentStatus.REFUNDED)
          || (latestRefund != null
              && ("COMPLETED".equalsIgnoreCase(latestRefund.getStatus())
                  || "DONE".equalsIgnoreCase(latestRefund.getStatus())))) {
        paymentStatus = "REFUNDED";
      } else if (order.getPaymentStatus() == PaymentStatus.PARTIALLY_REFUNDED
          || (payment != null && payment.getStatus() == PaymentStatus.PARTIALLY_REFUNDED)) {
        paymentStatus = "PARTIALLY_REFUNDED";
      } else if (payment != null) {
        paymentStatus = payment.getStatus().name();
      } else {
        paymentStatus = order.getPaymentStatus().name();
      }

      String refundLine = null;
      if ("REFUNDED".equals(paymentStatus) || "PARTIALLY_REFUNDED".equals(paymentStatus) || latestRefund != null) {
        if (latestRefund != null) {
          refundLine =
              "Refunded: "
                  + MONEY_FORMAT.format(latestRefund.getAmountCents() / 100.0)
                  + " on "
                  + DATE_FORMATTER.format(latestRefund.getCreatedAt());
        } else if (payment != null && payment.getStatus() == PaymentStatus.REFUNDED) {
          Instant refDate = order.getCancelledAt() != null ? order.getCancelledAt() : Instant.now();
          refundLine =
              "Refunded: "
                  + MONEY_FORMAT.format(payment.getAmountCents() / 100.0)
                  + " on "
                  + DATE_FORMATTER.format(refDate);
        }
      }

      StringBuilder metaBuilder = new StringBuilder();
      metaBuilder
          .append("Invoice #: INV-")
          .append(order.getOrderNumber())
          .append("\nInvoice Date: ")
          .append(DATE_FORMATTER.format(Instant.now()))
          .append("\nOrder #: ")
          .append(order.getOrderNumber())
          .append("\nOrder Date: ")
          .append(DATE_FORMATTER.format(order.getPlacedAt()))
          .append("\nOrder Status: ")
          .append(order.getStatus().name())
          .append("\nPayment Status: ")
          .append(paymentStatus);

      if (refundLine != null) {
        metaBuilder.append("\n").append(refundLine);
      }

      Paragraph metaPara = new Paragraph(metaBuilder.toString(), normalFont);
      metaPara.setAlignment(Element.ALIGN_RIGHT);
      metaPara.setSpacingBefore(4);
      invoiceMetaCell.addElement(metaPara);

      headerTable.addCell(invoiceMetaCell);
      headerTable.setSpacingAfter(15);
      document.add(headerTable);

      // CANCELLED Stamp if order is cancelled
      if (order.getStatus() == OrderStatus.CANCELLED) {
        PdfPTable stampTable = new PdfPTable(1);
        stampTable.setWidthPercentage(45);
        stampTable.setHorizontalAlignment(Element.ALIGN_CENTER);

        PdfPCell stampCell = new PdfPCell(new Phrase("CANCELLED", redStampFont));
        stampCell.setHorizontalAlignment(Element.ALIGN_CENTER);
        stampCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        stampCell.setBorderColor(new Color(220, 38, 38));
        stampCell.setBorderWidth(2.5f);
        stampCell.setPadding(6f);
        stampCell.setBackgroundColor(new Color(254, 242, 242));
        stampTable.addCell(stampCell);
        stampTable.setSpacingAfter(15);
        document.add(stampTable);
      }

      // 2. Bill To Customer
      Paragraph billToHeader = new Paragraph("Bill To:", headerFont);
      billToHeader.setSpacingAfter(4);
      document.add(billToHeader);

      Phrase customerPhrase = new Phrase("", normalFont);
      if (address != null) {
        customerPhrase.add(new Chunk("Recipient: ", boldFont));
        customerPhrase.add(createMixedPhrase(address.recipientName(), normalFont, khmerNormalFont));
        customerPhrase.add(new Chunk("\nPhone: " + (address.phone() != null ? address.phone() : "-") + "\nAddress: ", normalFont));
        String fullAddress =
            address.line1()
                + (address.district() != null ? ", " + address.district() : "")
                + (address.province() != null ? ", " + address.province() : "");
        customerPhrase.add(createMixedPhrase(fullAddress, normalFont, khmerNormalFont));
      } else if (user != null) {
        customerPhrase.add(createMixedPhrase(user.getFullName() != null ? user.getFullName() : "Customer", normalFont, khmerNormalFont));
        customerPhrase.add(new Chunk("\nEmail: " + user.getEmail(), normalFont));
      } else {
        customerPhrase.add(new Chunk("Customer", normalFont));
      }

      Paragraph customerPara = new Paragraph(customerPhrase);
      customerPara.setSpacingAfter(20);
      document.add(customerPara);

      // 3. Items Table
      PdfPTable table = new PdfPTable(5);
      table.setWidthPercentage(100);
      table.setWidths(new float[] {4.5f, 2f, 1.5f, 1f, 1.5f});

      addCell(table, new Phrase("Item Description", boldFont), Color.LIGHT_GRAY, Element.ALIGN_LEFT);
      addCell(table, new Phrase("SKU", boldFont), Color.LIGHT_GRAY, Element.ALIGN_LEFT);
      addCell(table, new Phrase("Unit Price", boldFont), Color.LIGHT_GRAY, Element.ALIGN_RIGHT);
      addCell(table, new Phrase("Qty", boldFont), Color.LIGHT_GRAY, Element.ALIGN_CENTER);
      addCell(table, new Phrase("Total", boldFont), Color.LIGHT_GRAY, Element.ALIGN_RIGHT);

      for (OrderItem item : items) {
        Product prod =
            item.getProductId() != null
                ? productRepository.findById(item.getProductId()).orElse(null)
                : null;
        String nameKm = prod != null ? prod.getNameKm() : null;

        Phrase itemDescPhrase = new Phrase("", normalFont);
        itemDescPhrase.add(createMixedPhrase(item.getProductNameSnapshot(), normalFont, khmerNormalFont));
        if (nameKm != null && !nameKm.isBlank()) {
          itemDescPhrase.add(new Chunk("\n", normalFont));
          itemDescPhrase.add(createMixedPhrase(nameKm, normalFont, khmerNormalFont));
        }

        addCell(table, itemDescPhrase, null, Element.ALIGN_LEFT);
        addCell(
            table,
            new Phrase(item.getSkuSnapshot() != null ? item.getSkuSnapshot() : "-", normalFont),
            null,
            Element.ALIGN_LEFT);
        addCell(
            table,
            new Phrase(MONEY_FORMAT.format(item.getUnitPriceCents() / 100.0), normalFont),
            null,
            Element.ALIGN_RIGHT);
        addCell(
            table,
            new Phrase(String.valueOf(item.getQuantity()), normalFont),
            null,
            Element.ALIGN_CENTER);
        addCell(
            table,
            new Phrase(MONEY_FORMAT.format(item.getLineTotalCents() / 100.0), normalFont),
            null,
            Element.ALIGN_RIGHT);
      }
      table.setSpacingAfter(15);
      document.add(table);

      // 4. Totals and Discount Breakdown
      PdfPTable totalsTable = new PdfPTable(2);
      totalsTable.setWidthPercentage(50);
      totalsTable.setHorizontalAlignment(Element.ALIGN_RIGHT);
      totalsTable.setWidths(new float[] {2.5f, 1.5f});

      addTotalRow(totalsTable, "Subtotal:", MONEY_FORMAT.format(order.getSubtotalCents() / 100.0), boldFont, false);

      // Parse discount breakdown snapshot if available
      boolean breakdownRendered = false;
      if (order.getDiscountBreakdown() != null && !order.getDiscountBreakdown().isBlank()) {
        try {
          Map<String, Object> breakdown =
              objectMapper.readValue(order.getDiscountBreakdown(), new TypeReference<>() {});
          if (breakdown.get("promotions") instanceof List<?> promoList) {
            for (Object obj : promoList) {
              if (obj instanceof Map<?, ?> pMap) {
                String label = String.valueOf(pMap.get("label"));
                Number amt = (Number) pMap.get("amountCents");
                if (amt != null && amt.longValue() > 0) {
                  addTotalRow(
                      totalsTable,
                      label + ":",
                      "-" + MONEY_FORMAT.format(amt.longValue() / 100.0),
                      normalFont,
                      false);
                  breakdownRendered = true;
                }
              }
            }
          }
          if (breakdown.get("coupon") instanceof Map<?, ?> cMap) {
            String code = String.valueOf(cMap.get("code"));
            Number amt = (Number) cMap.get("amountCents");
            if (amt != null && amt.longValue() > 0) {
              addTotalRow(
                  totalsTable,
                  "Coupon (" + code + "):",
                  "-" + MONEY_FORMAT.format(amt.longValue() / 100.0),
                  normalFont,
                  false);
              breakdownRendered = true;
            }
          }
        } catch (Exception ignored) {
        }
      }

      if (!breakdownRendered && order.getDiscountCents() > 0) {
        if (order.getCouponCode() != null && !order.getCouponCode().isBlank()) {
          addTotalRow(
              totalsTable,
              "Coupon (" + order.getCouponCode() + "):",
              "-" + MONEY_FORMAT.format(order.getDiscountCents() / 100.0),
              normalFont,
              false);
        } else {
          addTotalRow(
              totalsTable,
              "Discount:",
              "-" + MONEY_FORMAT.format(order.getDiscountCents() / 100.0),
              normalFont,
              false);
        }
      }

      addTotalRow(totalsTable, "Shipping:", MONEY_FORMAT.format(order.getShippingCents() / 100.0), normalFont, false);
      addTotalRow(totalsTable, "Total (USD):", MONEY_FORMAT.format(order.getTotalCents() / 100.0), boldFont, true);

      // Total in KHR with ៛ symbol
      long khrRate = appProperty.khqr().usdKhrRate() > 0 ? appProperty.khqr().usdKhrRate() : 4100L;
      long totalKhr = Math.round((order.getTotalCents() / 100.0) * khrRate);

      Phrase khrPhrase = new Phrase("", boldFont);
      khrPhrase.add(new Chunk("Total (KHR):", boldFont));
      PdfPCell khrLabelCell = new PdfPCell(khrPhrase);
      khrLabelCell.setBorder(PdfPCell.NO_BORDER);
      khrLabelCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
      khrLabelCell.setPadding(3f);
      totalsTable.addCell(khrLabelCell);

      Phrase khrValPhrase = new Phrase("", boldFont);
      khrValPhrase.add(new Chunk("៛ ", khmerBoldFont));
      khrValPhrase.add(new Chunk(KHR_FORMAT.format(totalKhr), boldFont));
      PdfPCell khrValCell = new PdfPCell(khrValPhrase);
      khrValCell.setBorder(PdfPCell.NO_BORDER);
      khrValCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
      khrValCell.setPadding(3f);
      totalsTable.addCell(khrValCell);

      // Exchange rate note
      PdfPCell rateCell = new PdfPCell(new Phrase("(exchange rate " + KHR_FORMAT.format(khrRate) + ")", smallGrayFont));
      rateCell.setColspan(2);
      rateCell.setBorder(PdfPCell.NO_BORDER);
      rateCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
      rateCell.setPadding(1f);
      totalsTable.addCell(rateCell);

      totalsTable.setSpacingAfter(20);
      document.add(totalsTable);

      // 5. Payment Details
      Paragraph paymentHeader = new Paragraph("Payment Details:", headerFont);
      paymentHeader.setSpacingAfter(4);
      document.add(paymentHeader);

      StringBuilder paymentDetails = new StringBuilder();
      paymentDetails.append("Payment Method: ").append(order.getPaymentMethod().name()).append("\n");

      if (payment != null && payment.getBakongHash() != null) {
        paymentDetails.append("Bakong Hash: ").append(payment.getBakongHash()).append("\n");
        if (payment.getPaidAt() != null) {
          paymentDetails.append("Paid At: ").append(DATE_FORMATTER.format(payment.getPaidAt())).append("\n");
        }
      }

      if (refundLine != null) {
        paymentDetails.append(refundLine).append("\n");
      }

      Paragraph paymentPara = new Paragraph(paymentDetails.toString(), normalFont);
      document.add(paymentPara);

      if (payment != null
          && payment.getBakongHash() != null
          && payment.getBakongHash().startsWith("MOCK-")) {
        Paragraph mockNote =
            new Paragraph(
                "Payment in MOCK mode — not a real transaction",
                new Font(latinBoldBase, 9, Font.NORMAL, new Color(180, 83, 9)));
        mockNote.setSpacingBefore(4);
        document.add(mockNote);
      }

      // 6. Footer
      Paragraph footer =
          new Paragraph(
              "\nThank you for shopping with KhmerMart!\nFor inquiries, contact support@khmermart.com",
              subTitleFont);
      footer.setAlignment(Element.ALIGN_CENTER);
      footer.setSpacingBefore(20);
      document.add(footer);

      document.close();
      return baos.toByteArray();
    } catch (Exception e) {
      log.error("Failed to generate invoice PDF for order: {}", orderNumber, e);
      throw new CustomException(
          ErrorCode.INTERNAL_ERROR, "Failed to generate invoice PDF: " + e.getMessage());
    }
  }

  private void addCell(PdfPTable table, Phrase phrase, Color bgColor, int alignment) {
    PdfPCell cell = new PdfPCell(phrase);
    if (bgColor != null) {
      cell.setBackgroundColor(bgColor);
    }
    cell.setHorizontalAlignment(alignment);
    cell.setPadding(6f);
    table.addCell(cell);
  }

  private void addTotalRow(
      PdfPTable table, String label, String value, Font font, boolean borderTop) {
    PdfPCell labelCell = new PdfPCell(new Phrase(label, font));
    labelCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
    labelCell.setPadding(3f);
    if (borderTop) {
      labelCell.setBorder(PdfPCell.TOP);
      labelCell.setBorderColor(Color.LIGHT_GRAY);
    } else {
      labelCell.setBorder(PdfPCell.NO_BORDER);
    }
    table.addCell(labelCell);

    PdfPCell valueCell = new PdfPCell(new Phrase(value, font));
    valueCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
    valueCell.setPadding(3f);
    if (borderTop) {
      valueCell.setBorder(PdfPCell.TOP);
      valueCell.setBorderColor(Color.LIGHT_GRAY);
    } else {
      valueCell.setBorder(PdfPCell.NO_BORDER);
    }
    table.addCell(valueCell);
  }

  private Phrase createMixedPhrase(String text, Font latinFont, Font khmerFont) {
    if (text == null || text.isEmpty()) {
      return new Phrase("", latinFont);
    }
    Phrase phrase = new Phrase("", latinFont);
    StringBuilder currentSegment = new StringBuilder();
    Boolean isCurrentKhmer = null;

    for (int i = 0; i < text.length(); ) {
      int cp = text.codePointAt(i);
      int charCount = Character.charCount(cp);

      boolean isKhmerChar = (cp >= 0x1780 && cp <= 0x17FF) || (cp >= 0x19E0 && cp <= 0x19FF);
      boolean isNeutral =
          Character.isWhitespace(cp)
              || cp == ':'
              || cp == ','
              || cp == '.'
              || cp == '-'
              || cp == '('
              || cp == ')'
              || cp == '/'
              || cp == '#'
              || cp == '+'
              || cp == '\n';

      if (isNeutral) {
        currentSegment.appendCodePoint(cp);
      } else {
        if (isCurrentKhmer == null) {
          isCurrentKhmer = isKhmerChar;
          currentSegment.appendCodePoint(cp);
        } else if (isCurrentKhmer == isKhmerChar) {
          currentSegment.appendCodePoint(cp);
        } else {
          if (currentSegment.length() > 0) {
            phrase.add(
                new Chunk(currentSegment.toString(), isCurrentKhmer ? khmerFont : latinFont));
            currentSegment.setLength(0);
          }
          isCurrentKhmer = isKhmerChar;
          currentSegment.appendCodePoint(cp);
        }
      }
      i += charCount;
    }

    if (currentSegment.length() > 0) {
      phrase.add(
          new Chunk(
              currentSegment.toString(),
              Boolean.TRUE.equals(isCurrentKhmer) ? khmerFont : latinFont));
    }
    return phrase;
  }
}
