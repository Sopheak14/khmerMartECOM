package com.khmermart;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class KhmerMartApplication {

  public static void main(String[] args) {
    SpringApplication.run(KhmerMartApplication.class, args);
  }
}
