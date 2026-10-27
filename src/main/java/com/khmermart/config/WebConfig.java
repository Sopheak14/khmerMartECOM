package com.khmermart.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.HandlerTypePredicate;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

  private final AppProperty appProperties;

  public WebConfig(AppProperty appProperties) {
    this.appProperties = appProperties;
  }

  @Override
  public void configurePathMatch(PathMatchConfigurer configurer) {
    configurer.addPathPrefix(
        "/api", HandlerTypePredicate.forBasePackage("com.khmermart.controller"));
  }

  @Override
  public void addResourceHandlers(ResourceHandlerRegistry registry) {
    String uploadPath = appProperties.uploadDir();
    if (!uploadPath.endsWith("/")) {
      uploadPath += "/";
    }
    registry.addResourceHandler("/uploads/**").addResourceLocations("file:" + uploadPath);
  }

  @Override
  public void addCorsMappings(CorsRegistry registry) {
    registry
        .addMapping("/**")
        .allowedOrigins(appProperties.frontendUrl())
        .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
        .allowedHeaders("Authorization", "Content-Type", "Idempotency-Key")
        .exposedHeaders("Content-Disposition", "X-Request-Id")
        .allowCredentials(true)
        .maxAge(3600);
  }
}
