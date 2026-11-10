package com.khmermart.config;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.util.Properties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.mail.javamail.JavaMailSender;

/** Guarantees tests never send real email, whatever MAIL_* env vars are set. */
@Configuration
@Profile("test")
public class TestMailConfig {

  @Bean
  @Primary
  public JavaMailSender javaMailSender() {
    JavaMailSender sender = mock(JavaMailSender.class);
    when(sender.createMimeMessage())
        .thenAnswer(inv -> new MimeMessage(Session.getInstance(new Properties())));
    return sender;
  }
}
