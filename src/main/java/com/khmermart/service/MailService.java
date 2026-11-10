package com.khmermart.service;

public interface MailService {

  void sendVerificationCodeEmail(String toEmail, String fullName, String code);

  void sendPasswordResetEmail(String toEmail, String fullName, String rawToken);
}
