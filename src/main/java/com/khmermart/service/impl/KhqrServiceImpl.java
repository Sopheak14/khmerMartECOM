package com.khmermart.service.impl;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import com.khmermart.common.exception.CustomException;
import com.khmermart.common.exception.ErrorCode;
import com.khmermart.config.AppProperty;
import com.khmermart.model.entity.Order;
import com.khmermart.model.entity.Payment;
import com.khmermart.model.enumerate.OrderStatus;
import com.khmermart.model.enumerate.PaymentMethod;
import com.khmermart.model.enumerate.PaymentStatus;
import com.khmermart.model.response.BakongCheckResponse;
import com.khmermart.model.response.KhqrPaymentDto;
import com.khmermart.model.response.PaymentStatusDto;
import com.khmermart.repository.OrderRepository;
import com.khmermart.repository.PaymentRepository;
import com.khmermart.security.JwtService;
import com.khmermart.service.BakongApiClient;
import com.khmermart.service.KhqrService;
import com.khmermart.service.PaymentConfirmationService;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import kh.gov.nbc.bakong_khqr.BakongKHQR;
import kh.gov.nbc.bakong_khqr.model.CRCValidation;
import kh.gov.nbc.bakong_khqr.model.IndividualInfo;
import kh.gov.nbc.bakong_khqr.model.KHQRCurrency;
import kh.gov.nbc.bakong_khqr.model.KHQRData;
import kh.gov.nbc.bakong_khqr.model.KHQRDeepLinkData;
import kh.gov.nbc.bakong_khqr.model.KHQRResponse;
import kh.gov.nbc.bakong_khqr.model.MerchantInfo;
import kh.gov.nbc.bakong_khqr.model.SourceInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class KhqrServiceImpl implements KhqrService {

  private static final Logger log = LoggerFactory.getLogger(KhqrServiceImpl.class);

  private final AppProperty appProperty;
  private final OrderRepository orderRepository;
  private final PaymentRepository paymentRepository;
  private final PaymentConfirmationService paymentConfirmationService;
  private final BakongApiClient bakongApiClient;
  private final JwtService jwtService;

  public KhqrServiceImpl(
      AppProperty appProperty,
      OrderRepository orderRepository,
      PaymentRepository paymentRepository,
      PaymentConfirmationService paymentConfirmationService,
      BakongApiClient bakongApiClient,
      JwtService jwtService) {
    this.appProperty = appProperty;
    this.orderRepository = orderRepository;
    this.paymentRepository = paymentRepository;
    this.paymentConfirmationService = paymentConfirmationService;
    this.bakongApiClient = bakongApiClient;
    this.jwtService = jwtService;
  }

  @Override
  @Transactional
  public KhqrPaymentDto generateKhqr(Long userId, String orderNumber, String currencyOverride) {
    Order order =
        orderRepository
            .findByOrderNumber(orderNumber)
            .orElseThrow(() -> new CustomException(ErrorCode.NOT_FOUND, "Order not found"));

    if (userId != null && !order.getUserId().equals(userId)) {
      throw new CustomException(ErrorCode.FORBIDDEN, "Access denied");
    }

    if (order.getStatus() != OrderStatus.PENDING_PAYMENT) {
      throw new CustomException(
          ErrorCode.INVALID_STATUS_TRANSITION,
          "Order is not in PENDING_PAYMENT status: " + order.getStatus());
    }

    Instant now = Instant.now();
    // Return active unexpired PENDING KHQR payment if one exists
    List<Payment> payments = paymentRepository.findByOrderIdOrderByIdDesc(order.getId());
    for (Payment p : payments) {
      if (p.getStatus() == PaymentStatus.PENDING
          && p.getMethod() == PaymentMethod.KHQR
          && p.getKhqrString() != null
          && p.getQrExpiresAt() != null
          && p.getQrExpiresAt().isAfter(now)) {
        String streamToken = jwtService.generateStreamToken(p.getId(), order.getUserId());
        String qrImageDataUrl = renderQrPngDataUrl(p.getKhqrString());
        Double amt =
            "USD".equalsIgnoreCase(p.getCurrency())
                ? BigDecimal.valueOf(p.getAmountCents())
                    .movePointLeft(2)
                    .setScale(2, RoundingMode.HALF_UP)
                    .doubleValue()
                : (p.getAmountKhr() != null ? p.getAmountKhr().doubleValue() : 0.0);

        return new KhqrPaymentDto(
            p.getId(),
            order.getOrderNumber(),
            p.getStatus(),
            "KHQR",
            p.getCurrency(),
            amt,
            p.getAmountCents(),
            p.getKhqrString(),
            qrImageDataUrl,
            p.getKhqrMd5(),
            p.getQrExpiresAt(),
            appProperty.khqr().merchantName(),
            generateDeepLink(p.getKhqrString()),
            appProperty.khqr().mode(),
            streamToken);
      }
    }

    // Reuse initial PENDING payment if created without KHQR string, otherwise create new
    Payment payment =
        payments.stream()
            .filter(
                p ->
                    p.getStatus() == PaymentStatus.PENDING
                        && p.getMethod() == PaymentMethod.KHQR
                        && p.getKhqrString() == null)
            .findFirst()
            .orElseGet(
                () ->
                    Payment.builder()
                        .orderId(order.getId())
                        .method(PaymentMethod.KHQR)
                        .status(PaymentStatus.PENDING)
                        .amountCents(order.getTotalCents())
                        .createdAt(now)
                        .build());

    return doGenerateKhqr(order, payment, currencyOverride);
  }

  @Override
  @Transactional
  public KhqrPaymentDto regenerateKhqr(Long userId, String orderNumber) {
    Order order =
        orderRepository
            .findByOrderNumber(orderNumber)
            .orElseThrow(() -> new CustomException(ErrorCode.NOT_FOUND, "Order not found"));

    if (userId != null && !order.getUserId().equals(userId)) {
      throw new CustomException(ErrorCode.FORBIDDEN, "Access denied");
    }

    if (order.getStatus() != OrderStatus.PENDING_PAYMENT) {
      throw new CustomException(
          ErrorCode.INVALID_STATUS_TRANSITION,
          "Order is no longer PENDING_PAYMENT: " + order.getStatus());
    }

    // Mark previous PENDING payments as EXPIRED
    List<Payment> existingPayments = paymentRepository.findByOrderIdOrderByIdDesc(order.getId());
    for (Payment p : existingPayments) {
      if (p.getStatus() == PaymentStatus.PENDING) {
        p.setStatus(PaymentStatus.EXPIRED);
        paymentRepository.save(p);
      }
    }

    // Create fresh Payment record
    Payment newPayment =
        Payment.builder()
            .orderId(order.getId())
            .method(PaymentMethod.KHQR)
            .status(PaymentStatus.PENDING)
            .amountCents(order.getTotalCents())
            .createdAt(Instant.now())
            .build();

    return doGenerateKhqr(order, newPayment, null);
  }

  private KhqrPaymentDto doGenerateKhqr(Order order, Payment payment, String currencyOverride) {
    AppProperty.Khqr props = appProperty.khqr();
    String currency =
        (currencyOverride != null && !currencyOverride.isBlank())
            ? currencyOverride.toUpperCase()
            : (props.currency() != null ? props.currency().toUpperCase() : "USD");

    boolean usd = "USD".equalsIgnoreCase(currency);
    long khrRate = props.usdKhrRate() > 0 ? props.usdKhrRate() : 4100L;

    double amount;
    Long amountKhr = null;
    if (usd) {
      amount =
          BigDecimal.valueOf(order.getTotalCents())
              .movePointLeft(2)
              .setScale(2, RoundingMode.HALF_UP)
              .doubleValue();
      amountKhr = Math.round((order.getTotalCents() / 100.0) * khrRate);
    } else {
      amount = Math.round((order.getTotalCents() / 100.0) * khrRate);
      amountKhr = (long) amount;
    }

    long expiresAtMs = Instant.now().plus(Duration.ofMinutes(props.expireMinutes())).toEpochMilli();
    Instant qrExpiresAt = Instant.ofEpochMilli(expiresAtMs);

    String qr;
    String md5;

    if ("MERCHANT".equalsIgnoreCase(props.type())) {
      MerchantInfo mInfo = new MerchantInfo();
      mInfo.setBakongAccountId(props.accountId());
      mInfo.setMerchantName(props.merchantName());
      mInfo.setMerchantCity(props.merchantCity());
      mInfo.setCurrency(usd ? KHQRCurrency.USD : KHQRCurrency.KHR);
      mInfo.setAmount(amount);
      mInfo.setBillNumber(order.getOrderNumber());
      mInfo.setMobileNumber(props.mobile());
      mInfo.setStoreLabel(props.storeLabel());
      mInfo.setTerminalLabel(props.terminalLabel());
      mInfo.setMerchantId(props.merchantId());
      mInfo.setAcquiringBank(props.acquiringBank());
      mInfo.setExpirationTimestamp(expiresAtMs);

      KHQRResponse<KHQRData> res = BakongKHQR.generateMerchant(mInfo);
      if (res.getKHQRStatus().getCode() != 0) {
        throw new CustomException(
            ErrorCode.KHQR_GENERATION_FAILED, res.getKHQRStatus().getMessage());
      }
      qr = res.getData().getQr();
      md5 = res.getData().getMd5();
    } else {
      IndividualInfo info = new IndividualInfo();
      info.setBakongAccountId(props.accountId());
      info.setMerchantName(props.merchantName());
      info.setMerchantCity(props.merchantCity());
      info.setCurrency(usd ? KHQRCurrency.USD : KHQRCurrency.KHR);
      info.setAmount(amount);
      info.setBillNumber(order.getOrderNumber());
      info.setMobileNumber(props.mobile());
      info.setStoreLabel(props.storeLabel());
      info.setTerminalLabel(props.terminalLabel());
      info.setExpirationTimestamp(expiresAtMs);

      KHQRResponse<KHQRData> res = BakongKHQR.generateIndividual(info);
      if (res.getKHQRStatus().getCode() != 0) {
        throw new CustomException(
            ErrorCode.KHQR_GENERATION_FAILED, res.getKHQRStatus().getMessage());
      }
      qr = res.getData().getQr();
      md5 = res.getData().getMd5();
    }

    // Verify generated KHQR
    KHQRResponse<CRCValidation> verifyRes = BakongKHQR.verify(qr);
    if (verifyRes == null || verifyRes.getData() == null || !verifyRes.getData().isValid()) {
      throw new CustomException(
          ErrorCode.KHQR_GENERATION_FAILED, "Generated KHQR CRC validation failed");
    }

    // Save payment
    payment.setMethod(PaymentMethod.KHQR);
    payment.setStatus(PaymentStatus.PENDING);
    payment.setAmountCents(order.getTotalCents());
    payment.setCurrency(currency);
    payment.setAmountKhr(amountKhr);
    payment.setKhqrString(qr);
    payment.setKhqrMd5(md5);
    payment.setQrExpiresAt(qrExpiresAt);
    payment = paymentRepository.save(payment);

    // Update order payment deadline (+1 min grace period)
    order.setPaymentDeadline(qrExpiresAt.plus(Duration.ofMinutes(1)));
    orderRepository.save(order);

    String streamToken = jwtService.generateStreamToken(payment.getId(), order.getUserId());
    String qrImageDataUrl = renderQrPngDataUrl(qr);
    String deepLink = generateDeepLink(qr);

    return new KhqrPaymentDto(
        payment.getId(),
        order.getOrderNumber(),
        payment.getStatus(),
        "KHQR",
        currency,
        amount,
        payment.getAmountCents(),
        qr,
        qrImageDataUrl,
        md5,
        qrExpiresAt,
        props.merchantName(),
        deepLink,
        props.mode(),
        streamToken);
  }

  @Override
  public PaymentStatusDto getPaymentStatus(Long userId, Long paymentId, boolean isStaffOrAdmin) {
    Payment payment =
        paymentRepository
            .findById(paymentId)
            .orElseThrow(() -> new CustomException(ErrorCode.NOT_FOUND, "Payment not found"));

    Order order =
        orderRepository
            .findById(payment.getOrderId())
            .orElseThrow(() -> new CustomException(ErrorCode.NOT_FOUND, "Order not found"));

    if (userId != null && !order.getUserId().equals(userId) && !isStaffOrAdmin) {
      throw new CustomException(ErrorCode.FORBIDDEN, "Access denied");
    }

    // In LIVE mode, trigger one immediate Bakong check if lastCheckedAt older than 3s
    if ("LIVE".equalsIgnoreCase(appProperty.khqr().mode())
        && payment.getStatus() == PaymentStatus.PENDING
        && payment.getKhqrMd5() != null) {
      Instant now = Instant.now();
      if (payment.getLastCheckedAt() == null
          || Duration.between(payment.getLastCheckedAt(), now).getSeconds() >= 3) {
        checkAndConfirmLivePayment(payment);
      }
    }

    return new PaymentStatusDto(
        payment.getId(),
        payment.getStatus(),
        order.getStatus(),
        payment.getPaidAt(),
        payment.getBakongHash(),
        payment.getQrExpiresAt());
  }

  @Override
  @Transactional
  public PaymentStatusDto simulatePaid(Long userId, Long paymentId, boolean isStaffOrAdmin) {
    if (!"MOCK".equalsIgnoreCase(appProperty.khqr().mode())) {
      throw new CustomException(ErrorCode.FORBIDDEN, "simulate-paid is only allowed in MOCK mode");
    }

    Payment payment =
        paymentRepository
            .findById(paymentId)
            .orElseThrow(() -> new CustomException(ErrorCode.NOT_FOUND, "Payment not found"));

    Order order =
        orderRepository
            .findById(payment.getOrderId())
            .orElseThrow(() -> new CustomException(ErrorCode.NOT_FOUND, "Order not found"));

    if (userId != null && !order.getUserId().equals(userId) && !isStaffOrAdmin) {
      throw new CustomException(ErrorCode.FORBIDDEN, "Access denied");
    }

    if (payment.getStatus() == PaymentStatus.PAID) {
      return new PaymentStatusDto(
          payment.getId(),
          payment.getStatus(),
          order.getStatus(),
          payment.getPaidAt(),
          payment.getBakongHash(),
          payment.getQrExpiresAt());
    }

    String mockHash = "MOCK-" + UUID.randomUUID();
    String fromAccount = "customer@mock";
    String toAccount = appProperty.khqr().accountId();
    String rawResponse = "{\"simulated\":true,\"hash\":\"" + mockHash + "\"}";

    Payment confirmedPayment =
        paymentConfirmationService.confirm(
            paymentId, mockHash, fromAccount, toAccount, rawResponse);

    Order confirmedOrder = orderRepository.findById(payment.getOrderId()).orElse(order);

    return new PaymentStatusDto(
        confirmedPayment.getId(),
        confirmedPayment.getStatus(),
        confirmedOrder.getStatus(),
        confirmedPayment.getPaidAt(),
        confirmedPayment.getBakongHash(),
        confirmedPayment.getQrExpiresAt());
  }

  private void checkAndConfirmLivePayment(Payment payment) {
    payment.setLastCheckedAt(Instant.now());
    payment.setCheckCount(payment.getCheckCount() + 1);
    paymentRepository.save(payment);

    BakongCheckResponse resp = bakongApiClient.checkTransactionByMd5(payment.getKhqrMd5());
    if (resp != null
        && resp.responseCode() != null
        && resp.responseCode() == 0
        && resp.data() != null) {
      // Validate toAccountId
      if (!appProperty.khqr().accountId().equalsIgnoreCase(resp.data().toAccountId())) {
        log.error(
            "Payment {} account mismatch: expected {}, got {}",
            payment.getId(),
            appProperty.khqr().accountId(),
            resp.data().toAccountId());
        payment.setStatus(PaymentStatus.FAILED);
        payment.setFailureReason("Recipient account mismatch");
        paymentRepository.save(payment);
        return;
      }

      // Validate currency
      if (!payment.getCurrency().equalsIgnoreCase(resp.data().currency())) {
        log.error(
            "Payment {} currency mismatch: expected {}, got {}",
            payment.getId(),
            payment.getCurrency(),
            resp.data().currency());
        payment.setStatus(PaymentStatus.FAILED);
        payment.setFailureReason("Currency mismatch");
        paymentRepository.save(payment);
        return;
      }

      // Validate amount
      BigDecimal expectedAmount =
          "USD".equalsIgnoreCase(payment.getCurrency())
              ? BigDecimal.valueOf(payment.getAmountCents())
                  .movePointLeft(2)
                  .setScale(2, RoundingMode.HALF_UP)
              : BigDecimal.valueOf(payment.getAmountKhr() != null ? payment.getAmountKhr() : 0);

      if (expectedAmount.compareTo(resp.data().amount()) != 0) {
        log.error(
            "Payment {} amount mismatch: expected {}, got {}",
            payment.getId(),
            expectedAmount,
            resp.data().amount());
        payment.setStatus(PaymentStatus.FAILED);
        payment.setFailureReason("Amount mismatch");
        paymentRepository.save(payment);
        return;
      }

      paymentConfirmationService.confirm(payment.getId(), resp.data());
    }
  }

  private String generateDeepLink(String qr) {
    if ("LIVE".equalsIgnoreCase(appProperty.khqr().mode())
        && appProperty.khqr().deeplinkUrl() != null
        && !appProperty.khqr().deeplinkUrl().isBlank()) {
      try {
        SourceInfo sourceInfo = new SourceInfo();
        sourceInfo.setAppName(appProperty.khqr().merchantName());
        KHQRResponse<KHQRDeepLinkData> dlRes =
            BakongKHQR.generateDeepLink(appProperty.khqr().deeplinkUrl(), qr, sourceInfo);
        if (dlRes != null && dlRes.getData() != null) {
          return dlRes.getData().getShortLink();
        }
      } catch (Exception ignored) {
      }
    }
    return null;
  }

  private String renderQrPngDataUrl(String qr) {
    try {
      QRCodeWriter qrWriter = new QRCodeWriter();
      BitMatrix bitMatrix =
          qrWriter.encode(
              qr,
              BarcodeFormat.QR_CODE,
              360,
              360,
              Map.of(
                  EncodeHintType.ERROR_CORRECTION,
                  ErrorCorrectionLevel.M,
                  EncodeHintType.MARGIN,
                  2,
                  EncodeHintType.CHARACTER_SET,
                  "UTF-8"));
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      MatrixToImageWriter.writeToStream(bitMatrix, "PNG", baos);
      return "data:image/png;base64," + Base64.getEncoder().encodeToString(baos.toByteArray());
    } catch (Exception e) {
      log.error("Failed to render QR code image", e);
      throw new CustomException(
          ErrorCode.KHQR_GENERATION_FAILED, "Failed to render QR image: " + e.getMessage());
    }
  }
}
