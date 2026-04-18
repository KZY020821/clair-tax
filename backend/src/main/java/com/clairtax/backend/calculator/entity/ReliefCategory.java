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

    @Column(nullable = false)
    private String type;

    @Column(nullable = false)
    private String code;

    @Column(nullable = false)
    private String section;

    @Column(name = "input_type", nullable = false)
    private String inputType;

    @Column(name = "unit_amount")
    private BigDecimal unitAmount;

    @Column(name = "max_quantity")
    private Integer maxQuantity;

    @Column(name = "display_order", nullable = false)
    private Integer displayOrder;

    @Column(name = "group_code")
    private String groupCode;

    @Column(name = "group_max_amount")
    private BigDecimal groupMaxAmount;

    @Column(name = "exclusive_group_code")
    private String exclusiveGroupCode;

    @Column(name = "requires_category_code")
    private String requiresCategoryCode;

    @Column(name = "auto_apply", nullable = false)
    private boolean autoApply;

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

    public String getCode() {
        return code;
    }

    public String getType() {
        return type;
    }

    public String getSection() {
        return section;
    }

    public String getInputType() {
        return inputType;
    }

    public BigDecimal getUnitAmount() {
        return unitAmount;
    }

    public Integer getMaxQuantity() {
        return maxQuantity;
    }

    public Integer getDisplayOrder() {
        return displayOrder;
    }

    public String getGroupCode() {
        return groupCode;
    }

    public BigDecimal getGroupMaxAmount() {
        return groupMaxAmount;
    }

    public String getExclusiveGroupCode() {
        return exclusiveGroupCode;
    }

    public String getRequiresCategoryCode() {
        return requiresCategoryCode;
    }

    public boolean isAutoApply() {
        return autoApply;
    }

    public boolean isRequiresReceipt() {
        return requiresReceipt;
    }
}
