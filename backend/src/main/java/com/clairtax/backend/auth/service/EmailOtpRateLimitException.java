package com.clairtax.backend.auth.service;

import java.time.Instant;

public class EmailOtpRateLimitException extends RuntimeException {

    private final Instant retryAt;

    public EmailOtpRateLimitException(String message, Instant retryAt) {
        super(message);
        this.retryAt = retryAt;
    }

    public Instant getRetryAt() {
        return retryAt;
    }
}
