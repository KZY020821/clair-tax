package com.clairtax.backend.auth.service;

public class MagicLinkVerificationException extends RuntimeException {

    private final MagicLinkFailureReason reason;

    public MagicLinkVerificationException(MagicLinkFailureReason reason) {
        super("Magic link verification failed: " + reason.name().toLowerCase());
        this.reason = reason;
    }

    public MagicLinkFailureReason getReason() {
        return reason;
    }
}
