package com.clairtax.backend.reliefclaim.entity;

import com.clairtax.backend.calculator.entity.ReliefCategory;
import com.clairtax.backend.useryear.entity.UserPolicyYear;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(
        name = "user_relief_claims",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uq_user_relief_claims_user_policy_year_category",
                        columnNames = {"user_policy_year_id", "relief_category_id"}
                )
        }
)
public class UserReliefClaim {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_policy_year_id", nullable = false)
    private UserPolicyYear userPolicyYear;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "relief_category_id", nullable = false)
    private ReliefCategory reliefCategory;

    @Column(name = "claimed_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal claimedAmount;

    protected UserReliefClaim() {
    }

    public UserReliefClaim(
            UserPolicyYear userPolicyYear,
            ReliefCategory reliefCategory,
            BigDecimal claimedAmount
    ) {
        this.userPolicyYear = userPolicyYear;
        this.reliefCategory = reliefCategory;
        this.claimedAmount = claimedAmount;
    }

    public UUID getId() {
        return id;
    }

    public UserPolicyYear getUserPolicyYear() {
        return userPolicyYear;
    }

    public ReliefCategory getReliefCategory() {
        return reliefCategory;
    }

    public BigDecimal getClaimedAmount() {
        return claimedAmount;
    }

    public void setClaimedAmount(BigDecimal claimedAmount) {
        this.claimedAmount = claimedAmount;
    }
}
