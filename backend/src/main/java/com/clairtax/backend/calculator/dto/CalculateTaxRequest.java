package com.clairtax.backend.calculator.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public record CalculateTaxRequest(
        @NotNull Integer policyYear,
        @NotNull @DecimalMin("0.00") BigDecimal grossIncome,
        @DecimalMin("0.00") BigDecimal zakat,
        List<@Valid ReliefClaimRequest> selectedReliefs
) {

    public CalculateTaxRequest {
        zakat = zakat == null ? BigDecimal.ZERO : zakat;
        selectedReliefs = selectedReliefs == null
                ? List.of()
                : Collections.unmodifiableList(new ArrayList<>(selectedReliefs));
    }
}
