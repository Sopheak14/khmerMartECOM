package com.khmermart.controller;

import com.khmermart.common.response.SuccessResponse;
import com.khmermart.config.AppProperty;
import com.khmermart.model.entity.PushSubscription;
import com.khmermart.model.request.PushSubscriptionRequest;
import com.khmermart.model.response.VapidKeyDto;
import com.khmermart.repository.PushSubscriptionRepository;
import com.khmermart.security.CurrentUser;
import com.khmermart.security.UserPrincipal;
import com.khmermart.util.ApiResponseUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/push")
@Tag(name = "Web Push", description = "Web push notification subscriptions")
public class PushController {

  private final PushSubscriptionRepository pushSubscriptionRepository;
  private final AppProperty appProperty;

  public PushController(
      PushSubscriptionRepository pushSubscriptionRepository, AppProperty appProperty) {
    this.pushSubscriptionRepository = pushSubscriptionRepository;
    this.appProperty = appProperty;
  }

  @GetMapping(value = {"/vapid-key", "/vapid-public-key"})
  @Operation(summary = "Get VAPID public key for web push subscription")
  public SuccessResponse<VapidKeyDto> getVapidPublicKey() {
    String publicKey =
        appProperty.vapid() != null
                && appProperty.vapid().publicKey() != null
                && !appProperty.vapid().publicKey().isBlank()
            ? appProperty.vapid().publicKey()
            : "BEl62iUYgUivxIkv69yViEuiBIa-Ib9-SkvMeAtA3LFgDzkrxZfjHKOqvM2624UbL1n530vdVeu42X-4E-q8-V0";
    return ApiResponseUtil.success(new VapidKeyDto(publicKey));
  }

  @PostMapping("/subscribe")
  @PreAuthorize("isAuthenticated()")
  @Operation(summary = "Subscribe device to web push notifications")
  public SuccessResponse<Map<String, Object>> subscribe(
      @CurrentUser UserPrincipal principal, @Valid @RequestBody PushSubscriptionRequest request) {
    PushSubscription sub =
        pushSubscriptionRepository
            .findByEndpoint(request.endpoint())
            .orElseGet(
                () ->
                    PushSubscription.builder()
                        .userId(principal.id())
                        .endpoint(request.endpoint())
                        .build());

    sub.setUserId(principal.id());
    sub.setP256dh(request.keys().p256dh());
    sub.setAuth(request.keys().auth());
    sub.setCreatedAt(Instant.now());
    pushSubscriptionRepository.save(sub);

    return ApiResponseUtil.success(Map.of());
  }
}
