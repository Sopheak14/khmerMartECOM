package com.khmermart.event.listener;

import com.khmermart.event.EmailOtpRequestedEvent;
import com.khmermart.service.MailService;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class EmailOtpEventListener {

  private final MailService mailService;

  public EmailOtpEventListener(MailService mailService) {
    this.mailService = mailService;
  }

  /** A mail failure is logged by MailService and never affects the committed user/code. */
  @Async("notificationExecutor")
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void handle(EmailOtpRequestedEvent event) {
    mailService.sendVerificationCodeEmail(event.email(), event.fullName(), event.code());
  }
}
