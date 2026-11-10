package com.khmermart.service.impl;

import com.khmermart.config.AppProperty;
import com.khmermart.service.MailService;
import com.khmermart.util.MailAddressUtil;
import jakarta.mail.internet.MimeMessage;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

@Service
public class MailServiceImpl implements MailService {

  private static final Logger log = LoggerFactory.getLogger(MailServiceImpl.class);

  private final Optional<JavaMailSender> mailSender;
  private final TemplateEngine templateEngine;
  private final AppProperty appProperties;

  public MailServiceImpl(
      Optional<JavaMailSender> mailSender,
      TemplateEngine templateEngine,
      AppProperty appProperties) {
    this.mailSender = mailSender;
    this.templateEngine = templateEngine;
    this.appProperties = appProperties;
  }

  @Async
  @Override
  public void sendVerificationCodeEmail(String toEmail, String fullName, String code) {
    if (mailSender.isEmpty()) {
      log.info("Mail sender not configured. Skipping OTP email to {}", maskEmail(toEmail));
      return;
    }
    try {
      Context context = new Context();
      context.setVariable("fullName", fullName);
      context.setVariable("code", code);
      String html = templateEngine.process("email/verify-otp", context);
      String text =
          "KhmerMart verification code / លេខកូដផ្ទៀងផ្ទាត់ KhmerMart\n\n"
              + "Hello "
              + fullName
              + ",\n\n"
              + "Your code / លេខកូដរបស់អ្នក: "
              + code
              + "\n\n"
              + "This code expires in 10 minutes. / កូដនេះផុតកំណត់ក្នុង ១០ នាទី។\n"
              + "If you did not sign up, ignore this email.\n"
              + "ប្រសិនបើអ្នកមិនបានចុះឈ្មោះទេ សូមបដិសេធអ៊ីមែលនេះ។\n";

      MimeMessage message = mailSender.get().createMimeMessage();
      MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
      helper.setFrom(MailAddressUtil.parseFrom(appProperties.mail().from()));
      helper.setTo(toEmail);
      helper.setSubject("KhmerMart verification code · លេខកូដផ្ទៀងផ្ទាត់ KhmerMart");
      helper.setText(text, html);

      mailSender.get().send(message);
      log.info("OTP sent to {}", maskEmail(toEmail));
    } catch (Exception e) {
      log.warn(
          "Failed to send OTP email to {}: {}: {}",
          maskEmail(toEmail),
          e.getClass().getSimpleName(),
          e.getMessage());
    }
  }

  /** u***@domain, so logs never hold the full address. */
  public static String maskEmail(String email) {
    if (email == null) {
      return "";
    }
    int at = email.indexOf('@');
    if (at <= 0) {
      return "***";
    }
    return email.charAt(0) + "***" + email.substring(at);
  }

  @Async
  @Override
  public void sendPasswordResetEmail(String toEmail, String fullName, String rawToken) {
    if (mailSender.isEmpty()) {
      log.info("Mail sender not configured. Skipping password reset email to {}", toEmail);
      return;
    }
    try {
      String resetUrl = appProperties.frontendUrl() + "/reset-password?token=" + rawToken;
      Context context = new Context();
      context.setVariable("fullName", fullName);
      context.setVariable("resetUrl", resetUrl);

      String content = templateEngine.process("email/reset-password", context);

      MimeMessage message = mailSender.get().createMimeMessage();
      MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
      helper.setFrom(MailAddressUtil.parseFrom(appProperties.mail().from()));
      helper.setTo(toEmail);
      helper.setSubject("Reset your password - KhmerMart");
      helper.setText(content, true);

      mailSender.get().send(message);
      log.info("Sent password reset email to {}", toEmail);
    } catch (Exception e) {
      log.warn("Failed to send password reset email to {}: {}: {}", toEmail, e.getClass().getSimpleName(), e.getMessage());
    }
  }
}
