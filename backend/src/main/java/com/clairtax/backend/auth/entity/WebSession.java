package com.clairtax.backend.auth.entity;

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
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "web_sessions")
public class WebSession {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @Column(name = "session_hash", nullable = false, unique = true, length = 64)
    private String sessionHash;

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    @Column(name = "last_seen_at", nullable = false)
    private OffsetDateTime lastSeenAt;

    @Column(name = "revoked_at")
    private OffsetDateTime revokedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected WebSession() {
    }

    public WebSession(AppUser user, String sessionHash, OffsetDateTime expiresAt, OffsetDateTime lastSeenAt) {
        this.user = user;
        this.sessionHash = sessionHash;
        this.expiresAt = expiresAt;
        this.lastSeenAt = lastSeenAt;
    }

    public UUID getId() {
        return id;
    }

    public AppUser getUser() {
        return user;
    }

    public String getSessionHash() {
        return sessionHash;
    }

    public OffsetDateTime getExpiresAt() {
        return expiresAt;
    }

    public OffsetDateTime getLastSeenAt() {
        return lastSeenAt;
    }

    public OffsetDateTime getRevokedAt() {
        return revokedAt;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public boolean isExpiredAt(OffsetDateTime timestamp) {
        return expiresAt.isBefore(timestamp);
    }

    public boolean isRevoked() {
        return revokedAt != null;
    }

    public void touchLastSeen(OffsetDateTime timestamp) {
        this.lastSeenAt = timestamp;
    }

    public void revoke(OffsetDateTime timestamp) {
        this.revokedAt = timestamp;
    }
}
