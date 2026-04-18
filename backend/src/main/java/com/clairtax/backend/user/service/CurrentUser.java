package com.clairtax.backend.user.service;

import java.util.UUID;

public record CurrentUser(
        UUID id,
        String email
) {
}
