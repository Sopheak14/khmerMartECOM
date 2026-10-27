package com.khmermart;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

// Auth is JWT-only: exclude the auto-configured in-memory user ("Using generated security
// password") so there is no default user/password anywhere.
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@ConfigurationPropertiesScan
public class KhmerMartApplication {

  public static void main(String[] args) {
    SpringApplication.run(KhmerMartApplication.class, args);
  }
}
