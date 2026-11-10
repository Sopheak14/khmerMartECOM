package com.khmermart.event;

/** Published inside the registering/resending transaction; mail goes out after commit. */
public record EmailOtpRequestedEvent(String email, String fullName, String code) {}
