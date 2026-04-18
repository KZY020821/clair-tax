package com.clairtax.backend.useryear.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record UserYearCategorySummaryResponse(
        UUID reliefCategoryId,
        String code,
        String name,
        String description,
        String section,
        BigDecimal maxAmount,
        BigDecimal claimedAmount,
        BigDecimal remainingAmount,
        boolean requiresReceipt,
        long receiptCount
) {
}
