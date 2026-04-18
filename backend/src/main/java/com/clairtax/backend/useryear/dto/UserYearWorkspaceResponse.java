package com.clairtax.backend.useryear.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record UserYearWorkspaceResponse(
        UUID id,
        Integer year,
        String status,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        int totalCategories,
        long totalReceiptCount,
        BigDecimal totalClaimedAmount,
        List<UserYearCategorySummaryResponse> categories
) {
}
