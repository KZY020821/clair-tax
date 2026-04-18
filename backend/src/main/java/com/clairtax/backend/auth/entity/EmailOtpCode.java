package com.clairtax.backend.auth.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "email_otp_codes")
public class EmailOtpCode {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String email;

    @Column(name = "device_id", length = 120)
    private String deviceId;

    @Column(name = "request_ip", length = 64)
    private String requestIp;

    @Column(name = "code_hash", nullable = false, length = 64)
    private String codeHash;

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    @Column(name = "consumed_at")
    private OffsetDateTime consumedAt;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "last_attempt_at")
    private OffsetDateTime lastAttemptAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected EmailOtpCode() {
    }

    public EmailOtpCode(
            String email,
            String deviceId,
            String requestIp,
            String codeHash,
            OffsetDateTime expiresAt
    ) {
        this.email = email;
        this.deviceId = deviceId;
        this.requestIp = requestIp;
        this.codeHash = codeHash;
        this.expiresAt = expiresAt;
        this.attemptCount = 0;
    }

    public UUID getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public String getRequestIp() {
        return requestIp;
    }

    public String getCodeHash() {
        return codeHash;
    }

    public OffsetDateTime getExpiresAt() {
        return expiresAt;
    }

    public OffsetDateTime getConsumedAt() {
        return consumedAt;
    }

    public int getAttemptCount() {
        return attemptCount;
    }

    public OffsetDateTime getLastAttemptAt() {
        return lastAttemptAt;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public boolean isExpiredAt(OffsetDateTime timestamp) {
        return expiresAt.isBefore(timestamp);
    }

    public boolean isConsumed() {
        return consumedAt != null;
    }

    public void registerFailedAttempt(OffsetDateTime attemptedAt) {
        this.attemptCount += 1;
        this.lastAttemptAt = attemptedAt;
    }

    public void markConsumed(OffsetDateTime consumedAt) {
        this.consumedAt = consumedAt;
    }
}
