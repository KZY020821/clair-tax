package com.clairtax.backend.auth.dto;

import java.time.Instant;

public record AuthErrorResponse(
        String message,
        Instant retryAt
) {
    public AuthErrorResponse(String message) {
        this(message, null);
    }
}
