package com.clairtax.backend.auth.dto;

import java.util.UUID;

public record AuthSessionResponse(
        boolean authenticated,
        UUID id,
        String email,
        String mode
) {
}
