package com.clairtax.backend.useryear.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record UserYearResponse(
        UUID id,
        Integer year,
        String status,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
