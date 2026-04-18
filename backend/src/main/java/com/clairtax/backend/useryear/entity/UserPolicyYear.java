package com.clairtax.backend.useryear.entity;

import com.clairtax.backend.policyyear.entity.PolicyYear;
import com.clairtax.backend.user.entity.AppUser;
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
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "user_policy_years",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uq_user_policy_years_user_policy_year",
                        columnNames = {"user_id", "policy_year_id"}
                )
        }
)
public class UserPolicyYear {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "policy_year_id", nullable = false)
    private PolicyYear policyYear;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected UserPolicyYear() {
    }

    public UserPolicyYear(AppUser user, PolicyYear policyYear) {
        this.user = user;
        this.policyYear = policyYear;
    }

    public UUID getId() {
        return id;
    }

    public AppUser getUser() {
        return user;
    }

    public PolicyYear getPolicyYear() {
        return policyYear;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}
