package com.clairtax.backend.auth.service;

public class EmailOtpVerificationException extends RuntimeException {

    private final EmailOtpFailureReason reason;

    public EmailOtpVerificationException(EmailOtpFailureReason reason) {
        this.reason = reason;
    }

    public EmailOtpFailureReason getReason() {
        return reason;
    }
}
