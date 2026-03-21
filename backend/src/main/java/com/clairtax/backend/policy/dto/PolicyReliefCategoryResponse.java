package com.clairtax.backend.policy.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record PolicyReliefCategoryResponse(
        UUID id,
        String name,
        String description,
        BigDecimal maxAmount,
        boolean requiresReceipt
) {
}
