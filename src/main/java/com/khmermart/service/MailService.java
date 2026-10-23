package com.khmermart.service;

public interface MailService {

  void sendVerificationEmail(String toEmail, String fullName, String rawToken);

  void sendPasswordResetEmail(String toEmail, String fullName, String rawToken);
}
