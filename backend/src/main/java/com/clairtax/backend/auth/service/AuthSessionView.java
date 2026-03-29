package com.clairtax.backend.auth.service;

import java.util.UUID;

public record AuthSessionView(
        UUID sessionId,
        UUID userId,
        String email
) {
}
