package com.clairtax.backend.calculator.dto;

import java.math.BigDecimal;
import java.util.List;

public record CalculateTaxResponse(
        Integer policyYear,
        BigDecimal grossIncome,
        BigDecimal totalRelief,
        BigDecimal taxableIncome,
        BigDecimal chargeableIncome,
        List<TaxBreakdownResponse> taxBreakdown,
        BigDecimal taxAmount,
        BigDecimal taxRebate,
        BigDecimal zakat,
        BigDecimal taxYouShouldPay,
        BigDecimal totalTaxPayable
) {
}
