package com.clairtax.backend.auth.service;

import java.time.Instant;

public record EmailOtpRequestResult(
        Instant resendAvailableAt,
        String debugCode
) {
}
