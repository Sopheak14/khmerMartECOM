package com.khmermart.controller;

import com.khmermart.config.AppProperty;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.HashMap;
import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

@RestController
@RequestMapping("/dev/email-preview")
@Profile({"dev", "test"})
@Tag(name = "Dev Tools", description = "Developer tools and email preview")
public class DevEmailPreviewController {

  private final TemplateEngine templateEngine;
  private final AppProperty appProperty;

  public DevEmailPreviewController(TemplateEngine templateEngine, AppProperty appProperty) {
    this.templateEngine = templateEngine;
    this.appProperty = appProperty;
  }

  @GetMapping(value = "/{template}", produces = MediaType.TEXT_HTML_VALUE + ";charset=UTF-8")
  @Operation(summary = "Render an HTML email preview in browser (dev profile only)")
  public String previewEmail(@PathVariable String template) {
    Context context = new Context();
    Map<String, Object> vars = getMockVariables(template);
    vars.forEach(context::setVariable);

    String templateName =
        template.endsWith(".html") ? template.substring(0, template.length() - 5) : template;
    return templateEngine.process("email/" + templateName, context);
  }

  private Map<String, Object> getMockVariables(String template) {
    Map<String, Object> vars = new HashMap<>();
    vars.put("frontendUrl", appProperty.frontendUrl());
    vars.put("fullName", "Sokha Meng");
    vars.put("orderNumber", "KM-20261023-000001");
    vars.put("subtotalCents", 99900L);
    vars.put("shippingCents", 150L);
    vars.put("totalCents", 100050L);
    vars.put("amountCents", 100050L);
    vars.put("bakongHash", "9F8A7B6C5D4E3F2A1B0C9D8E7F6A5B4C3D2E1F0A");
    vars.put("carrier", "KhmerMart Express");
    vars.put("trackingNumber", "KMTRK1029384756");
    vars.put("reason", "Customer requested cancellation before shipment");
    vars.put("productName", "Laptop Pro 15 (M3 Max, 32GB RAM)");
    vars.put("productId", 1001L);
    vars.put("sku", "LP15-SLV-512");
    vars.put("available", 3);
    vars.put("threshold", 5);
    vars.put(
        "adminReply",
        "Thank you for sharing your experience! We are glad you love the speed and screen quality.");
    vars.put("code", "482915");
    vars.put(
        "resetUrl", appProperty.frontendUrl() + "/reset-password?token=mock-reset-token-67890");
    return vars;
  }
}
