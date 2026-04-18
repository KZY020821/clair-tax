package com.clairtax.backend.auth.dto;

import java.time.Instant;

public record OtpRequestResponse(
        String message,
        Instant resendAvailableAt,
        String debugCode
) {
}
