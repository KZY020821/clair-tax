package com.clairtax.backend.calculator.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record ReliefClaimRequest(
        @NotNull UUID reliefCategoryId,
        @DecimalMin("0.00") BigDecimal claimedAmount,
        @Min(0) Integer quantity,
        Boolean selected
) {
}
