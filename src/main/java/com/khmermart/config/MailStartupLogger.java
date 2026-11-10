package com.khmermart.config;

import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Component;

/** Logs the effective mail transport once at startup. Never logs username or password. */
@Component
public class MailStartupLogger {

  private static final Logger log = LoggerFactory.getLogger(MailStartupLogger.class);

  private final Optional<JavaMailSender> mailSender;

  public MailStartupLogger(Optional<JavaMailSender> mailSender) {
    this.mailSender = mailSender;
  }

  @EventListener(ApplicationReadyEvent.class)
  public void logMailConfig() {
    if (mailSender.isPresent() && mailSender.get() instanceof JavaMailSenderImpl impl) {
      String auth = impl.getJavaMailProperties().getProperty("mail.smtp.auth", "false");
      log.info("Mail: host={} port={} auth={}", impl.getHost(), impl.getPort(), auth);
    } else if (mailSender.isPresent()) {
      log.info("Mail: custom sender {}", mailSender.get().getClass().getSimpleName());
    } else {
      log.info("Mail: not configured");
    }
  }
}
