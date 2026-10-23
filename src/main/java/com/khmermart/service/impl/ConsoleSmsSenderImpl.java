package com.khmermart.service.impl;

import com.khmermart.service.SmsSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class ConsoleSmsSenderImpl implements SmsSender {

  private static final Logger log = LoggerFactory.getLogger(ConsoleSmsSenderImpl.class);

  @Override
  public void sendSms(String phone, String message) {
    log.info("[SMS to {}] {}", phone, message);
  }
}
