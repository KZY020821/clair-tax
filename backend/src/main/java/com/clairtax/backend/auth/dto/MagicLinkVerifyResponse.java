package com.clairtax.backend.auth.dto;

import java.time.Instant;
import java.util.UUID;

public record MagicLinkVerifyResponse(
        String sessionToken,
        UUID userId,
        String email,
        Instant expiresAt
) {
}
