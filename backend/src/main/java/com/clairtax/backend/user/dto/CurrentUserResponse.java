package com.clairtax.backend.user.dto;

import java.util.UUID;

public record CurrentUserResponse(
        UUID id,
        String email,
        String mode
) {
}
