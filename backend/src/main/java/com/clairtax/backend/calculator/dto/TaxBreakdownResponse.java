package com.clairtax.backend.calculator.dto;

import java.math.BigDecimal;

public record TaxBreakdownResponse(
        BigDecimal minIncome,
        BigDecimal maxIncome,
        BigDecimal rate,
        BigDecimal taxableAmount,
        BigDecimal taxForBracket
) {
}
