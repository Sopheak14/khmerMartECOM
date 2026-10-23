package com.khmermart.service;

public interface SmsSender {
  void sendSms(String phone, String message);
}
