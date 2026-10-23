package com.khmermart.service.impl;

import com.khmermart.config.AppProperty;
import com.khmermart.service.MailService;
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
  public void sendVerificationEmail(String toEmail, String fullName, String rawToken) {
    if (mailSender.isEmpty()) {
      log.info("Mail sender not configured. Skipping verification email to {}", toEmail);
      return;
    }
    try {
      String verificationUrl = appProperties.frontendUrl() + "/verify-email?token=" + rawToken;
      Context context = new Context();
      context.setVariable("fullName", fullName);
      context.setVariable("verificationUrl", verificationUrl);

      String content = templateEngine.process("email/verify-email", context);

      MimeMessage message = mailSender.get().createMimeMessage();
      MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
      helper.setFrom(appProperties.mail().from());
      helper.setTo(toEmail);
      helper.setSubject("Verify your email address - KhmerMart");
      helper.setText(content, true);

      mailSender.get().send(message);
      log.info("Sent verification email to {}", toEmail);
    } catch (Exception e) {
      log.warn("Failed to send verification email to {}: {}", toEmail, e.getMessage());
    }
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
      helper.setFrom(appProperties.mail().from());
      helper.setTo(toEmail);
      helper.setSubject("Reset your password - KhmerMart");
      helper.setText(content, true);

      mailSender.get().send(message);
      log.info("Sent password reset email to {}", toEmail);
    } catch (Exception e) {
      log.warn("Failed to send password reset email to {}: {}", toEmail, e.getMessage());
    }
  }
}
