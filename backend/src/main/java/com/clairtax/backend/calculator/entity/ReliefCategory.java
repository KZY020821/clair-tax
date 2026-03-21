package com.clairtax.backend.calculator.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "relief_categories")
public class ReliefCategory {

    @Id
    private UUID id;

    @Column(name = "policy_year_id", nullable = false)
    private UUID policyYearId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String description;

    @Column(name = "max_amount", nullable = false)
    private BigDecimal maxAmount;

    @Column(name = "requires_receipt", nullable = false)
    private boolean requiresReceipt;

    protected ReliefCategory() {
    }

    public UUID getId() {
        return id;
    }

    public UUID getPolicyYearId() {
        return policyYearId;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public BigDecimal getMaxAmount() {
        return maxAmount;
    }

    public boolean isRequiresReceipt() {
        return requiresReceipt;
    }
}
