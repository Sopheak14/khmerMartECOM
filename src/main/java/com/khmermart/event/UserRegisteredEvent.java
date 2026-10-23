package com.khmermart.event;

public record UserRegisteredEvent(
    Long userId, String email, String fullName, String rawVerificationToken) {}
