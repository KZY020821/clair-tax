package com.clairtax.backend.calculator.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "tax_brackets")
public class TaxBracket {

    @Id
    private UUID id;

    @Column(name = "policy_year_id", nullable = false)
    private UUID policyYearId;

    @Column(name = "min_income", nullable = false)
    private BigDecimal minIncome;

    @Column(name = "max_income")
    private BigDecimal maxIncome;

    @Column(name = "tax_rate", nullable = false)
    private BigDecimal taxRate;

    protected TaxBracket() {
    }

    public UUID getId() {
        return id;
    }

    public UUID getPolicyYearId() {
        return policyYearId;
    }

    public BigDecimal getMinIncome() {
        return minIncome;
    }

    public BigDecimal getMaxIncome() {
        return maxIncome;
    }

    public BigDecimal getTaxRate() {
        return taxRate;
    }
}
