package com.khmermart.service.impl;

import com.khmermart.common.exception.CustomException;
import com.khmermart.common.exception.ErrorCode;
import com.khmermart.config.AppProperty;
import com.khmermart.model.entity.BankTransferSlip;
import com.khmermart.model.entity.Order;
import com.khmermart.model.entity.Payment;
import com.khmermart.model.enumerate.PaymentMethod;
import com.khmermart.model.enumerate.PaymentStatus;
import com.khmermart.model.response.OrderDto;
import com.khmermart.repository.BankTransferSlipRepository;
import com.khmermart.repository.OrderRepository;
import com.khmermart.repository.PaymentRepository;
import com.khmermart.service.BankTransferService;
import com.khmermart.service.OrderService;
import com.khmermart.service.PaymentConfirmationService;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class BankTransferServiceImpl implements BankTransferService {

  private static final Logger log = LoggerFactory.getLogger(BankTransferServiceImpl.class);

  private final OrderRepository orderRepository;
  private final PaymentRepository paymentRepository;
  private final BankTransferSlipRepository slipRepository;
  private final PaymentConfirmationService paymentConfirmationService;
  private final OrderService orderService;
  private final AppProperty appProperty;

  public BankTransferServiceImpl(
      OrderRepository orderRepository,
      PaymentRepository paymentRepository,
      BankTransferSlipRepository slipRepository,
      PaymentConfirmationService paymentConfirmationService,
      OrderService orderService,
      AppProperty appProperty) {
    this.orderRepository = orderRepository;
    this.paymentRepository = paymentRepository;
    this.slipRepository = slipRepository;
    this.paymentConfirmationService = paymentConfirmationService;
    this.orderService = orderService;
    this.appProperty = appProperty;
  }

  @Override
  @Transactional
  public OrderDto uploadSlip(Long userId, String orderNumber, MultipartFile file) {
    if (file == null || file.isEmpty()) {
      throw new CustomException(ErrorCode.VALIDATION_ERROR, "Slip file must not be empty");
    }

    String contentType = file.getContentType();
    if (contentType == null
        || (!contentType.startsWith("image/") && !contentType.equals("application/pdf"))) {
      throw new CustomException(
          ErrorCode.VALIDATION_ERROR, "Invalid file format. Only images and PDFs are allowed.");
    }

    Order order =
        orderRepository
            .findByOrderNumber(orderNumber)
            .orElseThrow(() -> new CustomException(ErrorCode.NOT_FOUND, "Order not found"));

    if (userId != null && !order.getUserId().equals(userId)) {
      throw new CustomException(ErrorCode.FORBIDDEN, "Access denied");
    }

    if (order.getPaymentMethod() != PaymentMethod.BANK_TRANSFER) {
      throw new CustomException(
          ErrorCode.VALIDATION_ERROR,
          "Order payment method is not BANK_TRANSFER: " + order.getPaymentMethod());
    }

    // Save file
    String uploadDir = appProperty.uploadDir();
    if (!uploadDir.endsWith("/")) {
      uploadDir += "/";
    }
    String slipsDir = uploadDir + "slips/";
    try {
      Path slipsPath = Paths.get(slipsDir);
      if (!Files.exists(slipsPath)) {
        Files.createDirectories(slipsPath);
      }

      String originalFilename = file.getOriginalFilename();
      String ext = "";
      if (originalFilename != null && originalFilename.contains(".")) {
        ext = originalFilename.substring(originalFilename.lastIndexOf("."));
      }
      String savedFilename = UUID.randomUUID() + ext;
      Path targetPath = slipsPath.resolve(savedFilename);
      Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);

      String fileUrl = "/uploads/slips/" + savedFilename;

      Payment payment =
          paymentRepository
              .findFirstByOrderIdOrderByIdDesc(order.getId())
              .orElseGet(
                  () ->
                      Payment.builder()
                          .orderId(order.getId())
                          .method(PaymentMethod.BANK_TRANSFER)
                          .status(PaymentStatus.PENDING)
                          .amountCents(order.getTotalCents())
                          .createdAt(Instant.now())
                          .build());

      payment.setStatus(PaymentStatus.PENDING_VERIFICATION);
      Payment savedPayment = paymentRepository.save(payment);
      Long finalPaymentId = savedPayment.getId();

      BankTransferSlip slip =
          slipRepository
              .findByPaymentId(finalPaymentId)
              .orElseGet(() -> BankTransferSlip.builder().paymentId(finalPaymentId).build());

      slip.setFileUrl(fileUrl);
      slip.setStatus("PENDING");
      slipRepository.save(slip);

      order.setPaymentStatus(PaymentStatus.PENDING_VERIFICATION);
      orderRepository.save(order);

      return orderService.toDto(order);
    } catch (IOException e) {
      log.error("Failed to store slip file", e);
      throw new CustomException(
          ErrorCode.INTERNAL_ERROR, "Failed to upload file: " + e.getMessage());
    }
  }

  @Override
  @Transactional
  public OrderDto approveBankTransfer(Long paymentId, Long adminUserId) {
    Payment payment =
        paymentRepository
            .findById(paymentId)
            .orElseThrow(() -> new CustomException(ErrorCode.NOT_FOUND, "Payment not found"));

    Order order =
        orderRepository
            .findById(payment.getOrderId())
            .orElseThrow(() -> new CustomException(ErrorCode.NOT_FOUND, "Order not found"));

    slipRepository
        .findByPaymentId(payment.getId())
        .ifPresent(
            slip -> {
              slip.setStatus("APPROVED");
              slip.setReviewedBy(adminUserId);
              slip.setReviewedAt(Instant.now());
              slipRepository.save(slip);
            });

    String mockHash = "BANK-" + UUID.randomUUID();
    String rawResponse = "{\"approvedBy\":" + adminUserId + "}";
    paymentConfirmationService.confirm(
        payment.getId(), mockHash, "bank_transfer", appProperty.khqr().accountId(), rawResponse);

    Order updatedOrder = orderRepository.findById(order.getId()).orElse(order);
    return orderService.toDto(updatedOrder);
  }
}
