package com.clairtax.backend.receipt.entity;

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
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "receipt_upload_intents")
public class ReceiptUploadIntent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_policy_year_id", nullable = false)
    private UserPolicyYear userPolicyYear;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "relief_category_id")
    private ReliefCategory reliefCategory;

    @Column(name = "object_key", nullable = false, unique = true)
    private String objectKey;

    @Column(name = "original_filename", nullable = false)
    private String originalFilename;

    @Column(name = "mime_type", nullable = false)
    private String mimeType;

    @Column(name = "file_size_bytes", nullable = false)
    private long fileSizeBytes;

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    @Column(name = "uploaded_at")
    private OffsetDateTime uploadedAt;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected ReceiptUploadIntent() {
    }

    public ReceiptUploadIntent(
            UserPolicyYear userPolicyYear,
            ReliefCategory reliefCategory,
            String objectKey,
            String originalFilename,
            String mimeType,
            long fileSizeBytes,
            OffsetDateTime expiresAt
    ) {
        this.userPolicyYear = userPolicyYear;
        this.reliefCategory = reliefCategory;
        this.objectKey = objectKey;
        this.originalFilename = originalFilename;
        this.mimeType = mimeType;
        this.fileSizeBytes = fileSizeBytes;
        this.expiresAt = expiresAt;
    }

    public void markUploaded() {
        this.uploadedAt = OffsetDateTime.now();
    }

    public void markCompleted() {
        this.completedAt = OffsetDateTime.now();
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

    public String getObjectKey() {
        return objectKey;
    }

    public String getOriginalFilename() {
        return originalFilename;
    }

    public String getMimeType() {
        return mimeType;
    }

    public long getFileSizeBytes() {
        return fileSizeBytes;
    }

    public OffsetDateTime getExpiresAt() {
        return expiresAt;
    }

    public OffsetDateTime getUploadedAt() {
        return uploadedAt;
    }

    public OffsetDateTime getCompletedAt() {
        return completedAt;
    }
}
