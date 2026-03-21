package com.clairtax.backend.policyyear.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "policy_year")
public class PolicyYear {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true)
    private Integer year;

    @Column(nullable = false)
    private String status;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected PolicyYear() {
    }

    public UUID getId() {
        return id;
    }

    public Integer getYear() {
        return year;
    }

    public String getStatus() {
        return status;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
