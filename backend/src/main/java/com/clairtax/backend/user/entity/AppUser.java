package com.clairtax.backend.user.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "users")
public class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "is_disabled", nullable = false)
    private boolean disabled;

    @Column(name = "marital_status", nullable = false)
    private String maritalStatus;

    @Column(name = "spouse_disabled")
    private Boolean spouseDisabled;

    @Column(name = "spouse_working")
    private Boolean spouseWorking;

    @Column(name = "has_children")
    private Boolean hasChildren;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "last_login_at")
    private OffsetDateTime lastLoginAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected AppUser() {
    }

    public AppUser(String email) {
        this.email = email;
        resetProfile();
    }

    public UUID getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public boolean isDisabled() {
        return disabled;
    }

    public MaritalStatus getMaritalStatus() {
        return MaritalStatus.fromValue(maritalStatus);
    }

    public Boolean getSpouseDisabled() {
        return spouseDisabled;
    }

    public Boolean getSpouseWorking() {
        return spouseWorking;
    }

    public Boolean getHasChildren() {
        return hasChildren;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getLastLoginAt() {
        return lastLoginAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void updateProfile(
            boolean disabled,
            MaritalStatus maritalStatus,
            Boolean spouseDisabled,
            Boolean spouseWorking,
            Boolean hasChildren
    ) {
        this.disabled = disabled;
        this.maritalStatus = maritalStatus.getValue();

        switch (maritalStatus) {
            case SINGLE -> {
                this.spouseDisabled = null;
                this.spouseWorking = null;
                this.hasChildren = null;
            }
            case MARRIED -> {
                this.spouseDisabled = Boolean.TRUE.equals(spouseDisabled);
                this.spouseWorking = Boolean.TRUE.equals(spouseWorking);
                this.hasChildren = Boolean.TRUE.equals(hasChildren);
            }
            case PREVIOUSLY_MARRIED -> {
                this.spouseDisabled = null;
                this.spouseWorking = null;
                this.hasChildren = Boolean.TRUE.equals(hasChildren);
            }
        }
    }

    public void resetProfile() {
        this.disabled = false;
        this.maritalStatus = MaritalStatus.SINGLE.getValue();
        this.spouseDisabled = null;
        this.spouseWorking = null;
        this.hasChildren = null;
    }
}
