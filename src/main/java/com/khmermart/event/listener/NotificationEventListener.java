package com.khmermart.event.listener;

import com.khmermart.event.InventoryLowEvent;
import com.khmermart.event.OrderCancelledEvent;
import com.khmermart.event.OrderDeliveredEvent;
import com.khmermart.event.OrderOutForDeliveryEvent;
import com.khmermart.event.OrderPlacedEvent;
import com.khmermart.event.OrderShippedEvent;
import com.khmermart.event.PasswordChangedEvent;
import com.khmermart.event.PaymentPaidEvent;
import com.khmermart.event.ReviewRepliedEvent;
import com.khmermart.model.entity.AppUser;
import com.khmermart.model.enumerate.NotificationChannel;
import com.khmermart.model.enumerate.Roles;
import com.khmermart.repository.AppUserRepository;
import com.khmermart.service.EmailSender;
import com.khmermart.service.NotificationService;
import com.khmermart.service.PushSender;
import com.khmermart.service.SmsSender;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class NotificationEventListener {

  private static final Logger log = LoggerFactory.getLogger(NotificationEventListener.class);

  private final NotificationService notificationService;
  private final EmailSender emailSender;
  private final SmsSender smsSender;
  private final PushSender pushSender;
  private final AppUserRepository appUserRepository;

  public NotificationEventListener(
      NotificationService notificationService,
      EmailSender emailSender,
      SmsSender smsSender,
      PushSender pushSender,
      AppUserRepository appUserRepository) {
    this.notificationService = notificationService;
    this.emailSender = emailSender;
    this.smsSender = smsSender;
    this.pushSender = pushSender;
    this.appUserRepository = appUserRepository;
  }

  @Async("notificationExecutor")
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2.0))
  public void handleOrderPlaced(OrderPlacedEvent event) {
    log.info("Handling OrderPlacedEvent for order: {}", event.orderNumber());

    // 1. In-App Notification
    notificationService.createNotification(
        event.userId(),
        NotificationChannel.IN_APP,
        "ORDER_PLACED",
        "Order Placed",
        "Your order " + event.orderNumber() + " has been placed successfully.",
        "/account/orders/" + event.orderNumber(),
        null);

    // 2. Email
    if (event.email() != null && !event.email().isBlank()) {
      Map<String, Object> vars = new HashMap<>();
      vars.put("fullName", event.fullName() != null ? event.fullName() : "Customer");
      vars.put("orderNumber", event.orderNumber());
      vars.put("subtotalCents", event.subtotalCents());
      vars.put("shippingCents", event.shippingCents());
      vars.put("totalCents", event.totalCents());

      emailSender.sendEmail(
          event.email(), "Order Confirmed - " + event.orderNumber(), "email/order-placed", vars);

      notificationService.createNotification(
          event.userId(),
          NotificationChannel.EMAIL,
          "ORDER_PLACED",
          "Order Confirmed - " + event.orderNumber(),
          "Order confirmation email sent to " + event.email(),
          "/account/orders/" + event.orderNumber(),
          null);
    }
  }

  @Async("notificationExecutor")
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2.0))
  public void handlePaymentPaid(PaymentPaidEvent event) {
    log.info("Handling PaymentPaidEvent for order: {}", event.orderNumber());

    // 1. In-App Notification
    notificationService.createNotification(
        event.userId(),
        NotificationChannel.IN_APP,
        "ORDER_PAID",
        "Payment Received",
        "Payment for order " + event.orderNumber() + " was confirmed.",
        "/account/orders/" + event.orderNumber(),
        null);

    // 2. Email with PDF invoice attachment
    if (event.email() != null && !event.email().isBlank()) {
      Map<String, Object> vars = new HashMap<>();
      vars.put("fullName", event.fullName() != null ? event.fullName() : "Customer");
      vars.put("orderNumber", event.orderNumber());
      vars.put("amountCents", event.amountCents());
      vars.put("bakongHash", event.bakongHash());

      String invoiceFilename = "invoice-" + event.orderNumber() + ".pdf";
      emailSender.sendEmailWithAttachment(
          event.email(),
          "Payment Received - " + event.orderNumber(),
          "email/payment-paid",
          vars,
          invoiceFilename,
          event.invoicePdf(),
          "application/pdf");

      notificationService.createNotification(
          event.userId(),
          NotificationChannel.EMAIL,
          "ORDER_PAID",
          "Payment Received - " + event.orderNumber(),
          "Payment confirmation with PDF invoice sent to " + event.email(),
          "/account/orders/" + event.orderNumber(),
          null);
    }

    // 3. SMS
    if (event.phone() != null && !event.phone().isBlank()) {
      String formattedAmount =
          String.format(java.util.Locale.US, "%.2f", event.amountCents() / 100.0);
      String smsText = "Your payment of $" + formattedAmount + " was successful";
      smsSender.sendSms(event.phone(), smsText);
      notificationService.createNotification(
          event.userId(),
          NotificationChannel.SMS,
          "ORDER_PAID",
          "Payment Received",
          smsText,
          "/account/orders/" + event.orderNumber(),
          null);
    }
  }

  @Async("notificationExecutor")
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2.0))
  public void handleOrderShipped(OrderShippedEvent event) {
    log.info("Handling OrderShippedEvent for order: {}", event.orderNumber());

    // 1. In-App Notification
    notificationService.createNotification(
        event.userId(),
        NotificationChannel.IN_APP,
        "ORDER_SHIPPED",
        "Order Shipped",
        "Your order "
            + event.orderNumber()
            + " has been shipped with "
            + event.carrier()
            + ". Tracking number: "
            + event.trackingNumber(),
        "/track/" + event.trackingNumber(),
        null);

    // 2. Email
    if (event.email() != null && !event.email().isBlank()) {
      Map<String, Object> vars = new HashMap<>();
      vars.put("fullName", event.fullName() != null ? event.fullName() : "Customer");
      vars.put("orderNumber", event.orderNumber());
      vars.put("carrier", event.carrier());
      vars.put("trackingNumber", event.trackingNumber());

      emailSender.sendEmail(
          event.email(), "Order Shipped - " + event.orderNumber(), "email/order-shipped", vars);

      notificationService.createNotification(
          event.userId(),
          NotificationChannel.EMAIL,
          "ORDER_SHIPPED",
          "Order Shipped - " + event.orderNumber(),
          "Shipment confirmation email sent to " + event.email(),
          "/track/" + event.trackingNumber(),
          null);
    }

    // 3. Web Push
    pushSender.sendPush(
        event.userId(),
        "Order Shipped",
        "Your order " + event.orderNumber() + " is on the way!",
        "/track/" + event.trackingNumber());
    notificationService.createNotification(
        event.userId(),
        NotificationChannel.PUSH,
        "ORDER_SHIPPED",
        "Order Shipped",
        "Your order " + event.orderNumber() + " has been shipped!",
        "/track/" + event.trackingNumber(),
        null);
  }

  @Async("notificationExecutor")
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2.0))
  public void handleOrderOutForDelivery(OrderOutForDeliveryEvent event) {
    log.info("Handling OrderOutForDeliveryEvent for order: {}", event.orderNumber());

    // 1. In-App Notification
    notificationService.createNotification(
        event.userId(),
        NotificationChannel.IN_APP,
        "ORDER_OUT_FOR_DELIVERY",
        "Out for Delivery",
        "Your order " + event.orderNumber() + " is out for delivery today!",
        "/track/" + event.trackingNumber(),
        null);

    // 2. Email
    if (event.email() != null && !event.email().isBlank()) {
      Map<String, Object> vars = new HashMap<>();
      vars.put("fullName", event.fullName() != null ? event.fullName() : "Customer");
      vars.put("orderNumber", event.orderNumber());
      vars.put("carrier", event.carrier());
      vars.put("trackingNumber", event.trackingNumber());

      emailSender.sendEmail(
          event.email(),
          "Out for Delivery - " + event.orderNumber(),
          "email/order-out-for-delivery",
          vars);

      notificationService.createNotification(
          event.userId(),
          NotificationChannel.EMAIL,
          "ORDER_OUT_FOR_DELIVERY",
          "Out for Delivery - " + event.orderNumber(),
          "Out for delivery notification sent to " + event.email(),
          "/track/" + event.trackingNumber(),
          null);
    }

    // 3. SMS
    if (event.phone() != null && !event.phone().isBlank()) {
      String smsText =
          "Your order "
              + event.orderNumber()
              + " is out for delivery with "
              + event.carrier()
              + "! KhmerMart";
      smsSender.sendSms(event.phone(), smsText);
      notificationService.createNotification(
          event.userId(),
          NotificationChannel.SMS,
          "ORDER_OUT_FOR_DELIVERY",
          "Out for Delivery",
          smsText,
          "/track/" + event.trackingNumber(),
          null);
    }

    // 4. Web Push
    pushSender.sendPush(
        event.userId(),
        "Out for Delivery",
        "Your order " + event.orderNumber() + " is out for delivery!",
        "/track/" + event.trackingNumber());
    notificationService.createNotification(
        event.userId(),
        NotificationChannel.PUSH,
        "ORDER_OUT_FOR_DELIVERY",
        "Out for Delivery",
        "Your order " + event.orderNumber() + " is out for delivery!",
        "/track/" + event.trackingNumber(),
        null);
  }

  @Async("notificationExecutor")
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2.0))
  public void handleOrderDelivered(OrderDeliveredEvent event) {
    log.info("Handling OrderDeliveredEvent for order: {}", event.orderNumber());

    // 1. In-App Notification
    notificationService.createNotification(
        event.userId(),
        NotificationChannel.IN_APP,
        "ORDER_DELIVERED",
        "Order Delivered",
        "Your order "
            + event.orderNumber()
            + " has been delivered. Thank you for shopping with KhmerMart!",
        "/account/orders/" + event.orderNumber(),
        null);

    // 2. Email
    if (event.email() != null && !event.email().isBlank()) {
      Map<String, Object> vars = new HashMap<>();
      vars.put("fullName", event.fullName() != null ? event.fullName() : "Customer");
      vars.put("orderNumber", event.orderNumber());

      emailSender.sendEmail(
          event.email(), "Order Delivered - " + event.orderNumber(), "email/order-delivered", vars);

      notificationService.createNotification(
          event.userId(),
          NotificationChannel.EMAIL,
          "ORDER_DELIVERED",
          "Order Delivered - " + event.orderNumber(),
          "Delivery confirmation email sent to " + event.email(),
          "/account/orders/" + event.orderNumber(),
          null);
    }

    // 3. SMS
    if (event.phone() != null && !event.phone().isBlank()) {
      String smsText =
          "Your order "
              + event.orderNumber()
              + " has been delivered. Thank you for shopping with KhmerMart!";
      smsSender.sendSms(event.phone(), smsText);
      notificationService.createNotification(
          event.userId(),
          NotificationChannel.SMS,
          "ORDER_DELIVERED",
          "Order Delivered",
          smsText,
          "/account/orders/" + event.orderNumber(),
          null);
    }

    // 4. Web Push
    pushSender.sendPush(
        event.userId(),
        "Order Delivered",
        "Your order " + event.orderNumber() + " has been delivered!",
        "/account/orders/" + event.orderNumber());
    notificationService.createNotification(
        event.userId(),
        NotificationChannel.PUSH,
        "ORDER_DELIVERED",
        "Order Delivered",
        "Your order " + event.orderNumber() + " has been delivered!",
        "/account/orders/" + event.orderNumber(),
        null);
  }

  @Async("notificationExecutor")
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2.0))
  public void handleOrderCancelled(OrderCancelledEvent event) {
    log.info("Handling OrderCancelledEvent for order: {}", event.orderNumber());

    // 1. In-App Notification
    notificationService.createNotification(
        event.userId(),
        NotificationChannel.IN_APP,
        "ORDER_CANCELLED",
        "Order Cancelled",
        "Order " + event.orderNumber() + " was cancelled. Reason: " + event.reason(),
        "/account/orders/" + event.orderNumber(),
        null);

    // 2. Email
    if (event.email() != null && !event.email().isBlank()) {
      Map<String, Object> vars = new HashMap<>();
      vars.put("fullName", event.fullName() != null ? event.fullName() : "Customer");
      vars.put("orderNumber", event.orderNumber());
      vars.put("reason", event.reason());

      emailSender.sendEmail(
          event.email(), "Order Cancelled - " + event.orderNumber(), "email/order-cancelled", vars);

      notificationService.createNotification(
          event.userId(),
          NotificationChannel.EMAIL,
          "ORDER_CANCELLED",
          "Order Cancelled - " + event.orderNumber(),
          "Cancellation email sent to " + event.email(),
          "/account/orders/" + event.orderNumber(),
          null);
    }
  }

  @Async("notificationExecutor")
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2.0))
  public void handleInventoryLow(InventoryLowEvent event) {
    log.info(
        "Handling InventoryLowEvent for product: {} (available: {}, threshold: {})",
        event.productName(),
        event.available(),
        event.threshold());

    List<AppUser> staffAndAdmins =
        appUserRepository.findByRoleIn(List.of(Roles.ADMIN, Roles.STAFF));
    String inAppBody =
        "Only " + event.available() + " left in stock (threshold " + event.threshold() + ")";

    // 1. In-App Notification for all ADMIN and STAFF users
    for (AppUser recipient : staffAndAdmins) {
      notificationService.createNotification(
          recipient.getId(),
          NotificationChannel.IN_APP,
          "INVENTORY_LOW",
          "Low Stock Alert: " + event.productName(),
          inAppBody,
          "/admin/products/" + event.productId(),
          null);
    }

    // 2. Email to Admin
    Map<String, Object> vars = new HashMap<>();
    vars.put("productName", event.productName());
    vars.put("sku", event.sku());
    vars.put("available", event.available());
    vars.put("threshold", event.threshold());

    emailSender.sendEmail(
        "admin@khmermart.local",
        "Low Stock Alert: " + event.productName() + " (" + event.sku() + ")",
        "email/low-stock-alert",
        vars);

    for (AppUser recipient : staffAndAdmins) {
      notificationService.createNotification(
          recipient.getId(),
          NotificationChannel.EMAIL,
          "INVENTORY_LOW",
          "Low Stock Alert: " + event.productName(),
          "Low stock email sent for " + event.productName(),
          "/admin/products/" + event.productId(),
          null);
    }
  }

  @Async("notificationExecutor")
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2.0))
  public void handleReviewReplied(ReviewRepliedEvent event) {
    log.info("Handling ReviewRepliedEvent for reviewId: {}", event.reviewId());

    // 1. In-App Notification
    notificationService.createNotification(
        event.userId(),
        NotificationChannel.IN_APP,
        "REVIEW_REPLIED",
        "Store Replied to Your Review",
        "KhmerMart staff replied to your review on "
            + event.productName()
            + ": \""
            + event.adminReply()
            + "\"",
        "/products/" + event.productId(),
        null);

    // 2. Email
    if (event.email() != null && !event.email().isBlank()) {
      Map<String, Object> vars = new HashMap<>();
      vars.put("fullName", event.fullName() != null ? event.fullName() : "Customer");
      vars.put("productName", event.productName());
      vars.put("productId", event.productId());
      vars.put("adminReply", event.adminReply());

      emailSender.sendEmail(
          event.email(), "Response to your review - KhmerMart", "email/review-replied", vars);

      notificationService.createNotification(
          event.userId(),
          NotificationChannel.EMAIL,
          "REVIEW_REPLIED",
          "Response to your review - KhmerMart",
          "Review reply notification email sent to " + event.email(),
          "/products/" + event.productId(),
          null);
    }
  }

  @Async("notificationExecutor")
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2.0))
  public void handlePasswordChanged(PasswordChangedEvent event) {
    log.info("Handling PasswordChangedEvent for userId: {}", event.userId());

    // 1. In-App Notification
    notificationService.createNotification(
        event.userId(),
        NotificationChannel.IN_APP,
        "PASSWORD_CHANGED",
        "Password Changed",
        "Your password was changed successfully. If you did not make this change, please contact support immediately.",
        "/account/security",
        null);

    // 2. Email
    if (event.email() != null && !event.email().isBlank()) {
      Map<String, Object> vars = new HashMap<>();
      vars.put("fullName", event.fullName() != null ? event.fullName() : "Customer");

      emailSender.sendEmail(
          event.email(), "Your password was changed - KhmerMart", "email/password-changed", vars);

      notificationService.createNotification(
          event.userId(),
          NotificationChannel.EMAIL,
          "PASSWORD_CHANGED",
          "Your password was changed - KhmerMart",
          "Password change alert email sent to " + event.email(),
          "/account/security",
          null);
    }
  }
}
