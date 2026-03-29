package com.clairtax.backend.auth.service;

import java.util.UUID;

public record VerifiedMagicLinkSession(
        UUID sessionId,
        UUID userId,
        String email,
        String sessionToken
) {
}
