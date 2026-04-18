package com.clairtax.backend.auth.dto;

public record MagicLinkRequestResponse(
        String message,
        String debugVerifyUrl
) {
    public MagicLinkRequestResponse(String message) {
        this(message, null);
    }
}
