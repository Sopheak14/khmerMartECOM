package com.khmermart.event;

public record PasswordChangedEvent(Long userId, String email, String fullName) {}
