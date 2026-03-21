package com.clairtax.backend.policyyear.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record PolicyYearResponse(
        UUID id,
        Integer year,
        String status,
        OffsetDateTime createdAt
) {
}
