package com.clairtax.backend.calculator.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record ReliefClaimRequest(
        @NotNull UUID reliefCategoryId,
        @NotNull @DecimalMin("0.00") BigDecimal claimedAmount
) {
}
