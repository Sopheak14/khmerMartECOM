package com.khmermart.service;

import java.util.Map;

public interface EmailSender {
  void sendEmail(
      String toEmail, String subject, String templateName, Map<String, Object> variables);

  void sendEmailWithAttachment(
      String toEmail,
      String subject,
      String templateName,
      Map<String, Object> variables,
      String attachmentFilename,
      byte[] attachmentBytes,
      String contentType);
}
