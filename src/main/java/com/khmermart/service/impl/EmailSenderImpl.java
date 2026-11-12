package com.khmermart.service.impl;

import com.khmermart.config.AppProperty;
import com.khmermart.service.EmailSender;
import com.khmermart.util.MailAddressUtil;
import jakarta.mail.internet.MimeMessage;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

@Service
public class EmailSenderImpl implements EmailSender {

  private static final Logger log = LoggerFactory.getLogger(EmailSenderImpl.class);

  private final Optional<JavaMailSender> mailSender;
  private final TemplateEngine templateEngine;
  private final AppProperty appProperty;

  public EmailSenderImpl(
      Optional<JavaMailSender> mailSender, TemplateEngine templateEngine, AppProperty appProperty) {
    this.mailSender = mailSender;
    this.templateEngine = templateEngine;
    this.appProperty = appProperty;
  }

  @Override
  public void sendEmail(
      String toEmail, String subject, String templateName, Map<String, Object> variables) {
    sendEmailWithAttachment(toEmail, subject, templateName, variables, null, null, null);
  }

  @Override
  public void sendEmailWithAttachment(
      String toEmail,
      String subject,
      String templateName,
      Map<String, Object> variables,
      String attachmentFilename,
      byte[] attachmentBytes,
      String contentType) {
    if (mailSender.isEmpty()) {
      log.info(
          "Mail sender not configured. Skipping email to {} with subject '{}'", toEmail, subject);
      return;
    }

    try {
      Context context = new Context();
      if (variables != null) {
        variables.forEach(context::setVariable);
      }
      if (!context.containsVariable("frontendUrl")) {
        context.setVariable("frontendUrl", appProperty.frontendUrl());
      }

      String templatePath =
          templateName.startsWith("email/") ? templateName : "email/" + templateName;
      String htmlContent = templateEngine.process(templatePath, context);

      MimeMessage message = mailSender.get().createMimeMessage();
      boolean isMultipart = (attachmentBytes != null && attachmentBytes.length > 0);
      MimeMessageHelper helper = new MimeMessageHelper(message, isMultipart, "UTF-8");

      helper.setFrom(MailAddressUtil.parseFrom(appProperty.mail().from()));
      helper.setTo(toEmail);
      helper.setSubject(subject);
      helper.setText(htmlContent, true);

      if (isMultipart && attachmentFilename != null) {
        helper.addAttachment(
            attachmentFilename,
            new ByteArrayResource(attachmentBytes),
            contentType != null ? contentType : "application/pdf");
      }

      mailSender.get().send(message);
      log.info("Sent email to {} with subject '{}'", toEmail, subject);
    } catch (Exception e) {
      log.error(
          "Failed to send email to {}: {}: {}",
          toEmail,
          e.getClass().getSimpleName(),
          e.getMessage());
      throw new RuntimeException("Email delivery failed: " + e.getMessage(), e);
    }
  }
}
