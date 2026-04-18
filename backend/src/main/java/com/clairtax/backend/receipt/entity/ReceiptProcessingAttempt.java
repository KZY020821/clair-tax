package com.clairtax.backend.receipt.entity;

import com.clairtax.backend.receipt.model.ReceiptProcessingAttemptStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
        name = "receipt_processing_attempts",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_receipt_processing_attempts_job_id", columnNames = {"job_id"})
        }
)
public class ReceiptProcessingAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "receipt_id", nullable = false)
    private Receipt receipt;

    @Column(name = "job_id", nullable = false)
    private UUID jobId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ReceiptProcessingAttemptStatus status;

    @Column(name = "error_code", length = 120)
    private String errorCode;

    @Column(name = "error_message", length = 2000)
    private String errorMessage;

    @Column(name = "started_at")
    private OffsetDateTime startedAt;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected ReceiptProcessingAttempt() {
    }

    public ReceiptProcessingAttempt(
            Receipt receipt,
            UUID jobId,
            ReceiptProcessingAttemptStatus status
    ) {
        this.receipt = receipt;
        this.jobId = jobId;
        this.status = status;
    }

    public void updateStatus(
            ReceiptProcessingAttemptStatus status,
            String errorCode,
            String errorMessage
    ) {
        this.status = status;
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;

        if (status == ReceiptProcessingAttemptStatus.PROCESSING) {
            this.startedAt = OffsetDateTime.now();
        }
        if (status == ReceiptProcessingAttemptStatus.COMPLETED || status == ReceiptProcessingAttemptStatus.FAILED) {
            this.completedAt = OffsetDateTime.now();
        }
    }

    public Receipt getReceipt() {
        return receipt;
    }

    public UUID getJobId() {
        return jobId;
    }

    public ReceiptProcessingAttemptStatus getStatus() {
        return status;
    }
}
