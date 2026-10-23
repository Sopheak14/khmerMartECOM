package com.khmermart.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.khmermart.common.exception.CustomException;
import com.khmermart.common.exception.ErrorCode;
import com.khmermart.config.AppProperty;
import com.khmermart.model.entity.AppUser;
import com.khmermart.model.entity.Order;
import com.khmermart.model.entity.OrderItem;
import com.khmermart.model.entity.Payment;
import com.khmermart.model.response.AddressDto;
import com.khmermart.repository.AppUserRepository;
import com.khmermart.repository.OrderItemRepository;
import com.khmermart.repository.OrderRepository;
import com.khmermart.repository.PaymentRepository;
import com.khmermart.service.InvoiceService;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.text.DecimalFormat;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InvoiceServiceImpl implements InvoiceService {

  private final OrderRepository orderRepository;
  private final OrderItemRepository orderItemRepository;
  private final PaymentRepository paymentRepository;
  private final AppUserRepository userRepository;
  private final AppProperty appProperty;
  private final ObjectMapper objectMapper;

  private static final DateTimeFormatter DATE_FORMATTER =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.of("Asia/Phnom_Penh"));

  private static final DecimalFormat MONEY_FORMAT = new DecimalFormat("$#,##0.00");
  private static final DecimalFormat KHR_FORMAT = new DecimalFormat("#,##0");

  public InvoiceServiceImpl(
      OrderRepository orderRepository,
      OrderItemRepository orderItemRepository,
      PaymentRepository paymentRepository,
      AppUserRepository userRepository,
      AppProperty appProperty,
      ObjectMapper objectMapper) {
    this.orderRepository = orderRepository;
    this.orderItemRepository = orderItemRepository;
    this.paymentRepository = paymentRepository;
    this.userRepository = userRepository;
    this.appProperty = appProperty;
    this.objectMapper = objectMapper;
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

      Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 22, Color.DARK_GRAY);
      Font subTitleFont = FontFactory.getFont(FontFactory.HELVETICA, 12, Color.GRAY);
      Font headerFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, Color.BLACK);
      Font boldFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, Color.BLACK);
      Font normalFont = FontFactory.getFont(FontFactory.HELVETICA, 10, Color.BLACK);

      // Header
      Paragraph title = new Paragraph("KhmerMart - Online Store", titleFont);
      title.setAlignment(Element.ALIGN_LEFT);
      document.add(title);

      Paragraph invoiceLabel = new Paragraph("OFFICIAL INVOICE", headerFont);
      invoiceLabel.setSpacingBefore(10);
      document.add(invoiceLabel);

      Paragraph info =
          new Paragraph(
              "Invoice #: INV-"
                  + order.getOrderNumber()
                  + "\nOrder #: "
                  + order.getOrderNumber()
                  + "\nOrder Date: "
                  + DATE_FORMATTER.format(order.getPlacedAt())
                  + "\nOrder Status: "
                  + order.getStatus().name()
                  + "\nPayment Status: "
                  + order.getPaymentStatus().name(),
              normalFont);
      info.setSpacingAfter(15);
      document.add(info);

      // Customer Info
      Paragraph billToHeader = new Paragraph("Bill To:", headerFont);
      document.add(billToHeader);

      String customerText =
          (address != null
              ? address.recipientName()
                  + "\nPhone: "
                  + address.phone()
                  + "\nAddress: "
                  + address.line1()
                  + (address.district() != null ? ", " + address.district() : "")
                  + ", "
                  + address.province()
              : (user != null ? user.getFullName() + "\nEmail: " + user.getEmail() : "Customer"));
      Paragraph customerPara = new Paragraph(customerText, normalFont);
      customerPara.setSpacingAfter(20);
      document.add(customerPara);

      // Table of Items
      PdfPTable table = new PdfPTable(5);
      table.setWidthPercentage(100);
      table.setWidths(new float[] {4f, 2f, 1.5f, 1f, 1.5f});

      // Table Header
      addCell(table, "Item Description", boldFont, Color.LIGHT_GRAY, Element.ALIGN_LEFT);
      addCell(table, "SKU", boldFont, Color.LIGHT_GRAY, Element.ALIGN_LEFT);
      addCell(table, "Unit Price", boldFont, Color.LIGHT_GRAY, Element.ALIGN_RIGHT);
      addCell(table, "Qty", boldFont, Color.LIGHT_GRAY, Element.ALIGN_CENTER);
      addCell(table, "Total", boldFont, Color.LIGHT_GRAY, Element.ALIGN_RIGHT);

      for (OrderItem item : items) {
        addCell(table, item.getProductNameSnapshot(), normalFont, null, Element.ALIGN_LEFT);
        addCell(
            table,
            item.getSkuSnapshot() != null ? item.getSkuSnapshot() : "-",
            normalFont,
            null,
            Element.ALIGN_LEFT);
        addCell(
            table,
            MONEY_FORMAT.format(item.getUnitPriceCents() / 100.0),
            normalFont,
            null,
            Element.ALIGN_RIGHT);
        addCell(table, String.valueOf(item.getQuantity()), normalFont, null, Element.ALIGN_CENTER);
        addCell(
            table,
            MONEY_FORMAT.format(item.getLineTotalCents() / 100.0),
            normalFont,
            null,
            Element.ALIGN_RIGHT);
      }
      table.setSpacingAfter(15);
      document.add(table);

      // Totals
      long khrRate = appProperty.khqr().usdKhrRate() > 0 ? appProperty.khqr().usdKhrRate() : 4100L;
      long totalKhr = Math.round((order.getTotalCents() / 100.0) * khrRate);

      Paragraph totalsPara =
          new Paragraph(
              "Subtotal: "
                  + MONEY_FORMAT.format(order.getSubtotalCents() / 100.0)
                  + "\nDiscount: -"
                  + MONEY_FORMAT.format(order.getDiscountCents() / 100.0)
                  + "\nShipping: "
                  + MONEY_FORMAT.format(order.getShippingCents() / 100.0)
                  + "\nTotal (USD): "
                  + MONEY_FORMAT.format(order.getTotalCents() / 100.0)
                  + "\nTotal (KHR): KHR "
                  + KHR_FORMAT.format(totalKhr)
                  + " (exchange rate "
                  + KHR_FORMAT.format(khrRate)
                  + ")",
              boldFont);
      totalsPara.setAlignment(Element.ALIGN_RIGHT);
      totalsPara.setSpacingAfter(20);
      document.add(totalsPara);

      // Payment Details
      StringBuilder paymentDetails = new StringBuilder();
      paymentDetails
          .append("Payment Method: ")
          .append(order.getPaymentMethod().name())
          .append("\n");
      if (payment != null && payment.getBakongHash() != null) {
        paymentDetails.append("Bakong Hash: ").append(payment.getBakongHash()).append("\n");
        if (payment.getPaidAt() != null) {
          paymentDetails
              .append("Paid At: ")
              .append(DATE_FORMATTER.format(payment.getPaidAt()))
              .append("\n");
        }
      }
      Paragraph paymentPara = new Paragraph(paymentDetails.toString(), normalFont);
      document.add(paymentPara);

      // Footer
      Paragraph footer =
          new Paragraph(
              "\nThank you for shopping with KhmerMart!\nFor inquiries, contact support@khmermart.com",
              subTitleFont);
      footer.setAlignment(Element.ALIGN_CENTER);
      document.add(footer);

      document.close();
      return baos.toByteArray();
    } catch (Exception e) {
      throw new CustomException(
          ErrorCode.INTERNAL_ERROR, "Failed to generate invoice PDF: " + e.getMessage());
    }
  }

  private void addCell(PdfPTable table, String text, Font font, Color bgColor, int alignment) {
    PdfPCell cell = new PdfPCell(new Phrase(text, font));
    if (bgColor != null) {
      cell.setBackgroundColor(bgColor);
    }
    cell.setHorizontalAlignment(alignment);
    cell.setPadding(5f);
    table.addCell(cell);
  }
}
