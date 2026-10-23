package com.khmermart.model.request;

import jakarta.validation.constraints.NotBlank;

public record PushKeysRequest(
    @NotBlank(message = "p256dh key is required") String p256dh,
    @NotBlank(message = "auth key is required") String auth) {}
