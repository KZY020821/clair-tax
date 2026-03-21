package com.clairtax.backend.policy.dto;

import java.util.List;
import java.util.UUID;

public record PolicyResponse(
        UUID id,
        Integer year,
        String status,
        List<PolicyReliefCategoryResponse> reliefCategories
) {
}
