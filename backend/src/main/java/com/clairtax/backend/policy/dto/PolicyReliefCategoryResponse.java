package com.clairtax.backend.policy.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record PolicyReliefCategoryResponse(
        UUID id,
        String code,
        String name,
        String description,
        String section,
        String inputType,
        BigDecimal unitAmount,
        BigDecimal maxAmount,
        Integer maxQuantity,
        Integer displayOrder,
        String groupCode,
        BigDecimal groupMaxAmount,
        String exclusiveGroupCode,
        String requiresCategoryCode,
        boolean autoApply,
        boolean requiresReceipt
) {
}
