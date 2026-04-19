package com.clairtax.backend.receipt.entity;

import com.clairtax.backend.calculator.entity.ReliefCategory;
import com.clairtax.backend.receipt.model.ReceiptStatus;
import com.clairtax.backend.useryear.entity.UserPolicyYear;
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
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "receipts")
public class Receipt {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_policy_year_id", nullable = false)
    private UserPolicyYear userPolicyYear;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "relief_category_id")
    private ReliefCategory reliefCategory;

    @Column(name = "merchant_name")
    private String merchantName;

    @Column(name = "receipt_date")
    private LocalDate receiptDate;

    @Column(precision = 14, scale = 2)
    private BigDecimal amount;

    @Column(name = "currency_code", length = 3)
    private String currencyCode;

    @Column(length = 2000)
    private String notes;

    @Column(name = "file_name", nullable = false)
    private String fileName;

    @Column(name = "file_url", nullable = false)
    private String fileUrl;

    @Column(name = "s3_bucket", nullable = false)
    private String s3Bucket;

    @Column(name = "s3_key", nullable = false, unique = true)
    private String s3Key;

    @Column(name = "mime_type", nullable = false)
    private String mimeType;

    @Column(name = "file_size_bytes", nullable = false)
    private long fileSizeBytes;

    @Column(name = "sha256_hash", nullable = false, length = 64)
    private String sha256Hash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ReceiptStatus status;

    @Column(name = "processing_error_code", length = 120)
    private String processingErrorCode;

    @Column(name = "processing_error_message", length = 2000)
    private String processingErrorMessage;

    @Column(name = "uploaded_at", nullable = false)
    private OffsetDateTime uploadedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected Receipt() {
    }

    public Receipt(
            UserPolicyYear userPolicyYear,
            ReliefCategory reliefCategory,
            String notes,
            String fileName,
            String fileUrl,
            String s3Bucket,
            String s3Key,
            String mimeType,
            long fileSizeBytes,
            String sha256Hash,
            ReceiptStatus status,
            OffsetDateTime uploadedAt
    ) {
        this.userPolicyYear = userPolicyYear;
        this.reliefCategory = reliefCategory;
        this.notes = notes;
        this.fileName = fileName;
        this.fileUrl = fileUrl;
        this.s3Bucket = s3Bucket;
        this.s3Key = s3Key;
        this.mimeType = mimeType;
        this.fileSizeBytes = fileSizeBytes;
        this.sha256Hash = sha256Hash;
        this.status = status;
        this.uploadedAt = uploadedAt;
    }

    public void confirmReview(
            UserPolicyYear userPolicyYear,
            ReliefCategory reliefCategory,
            String merchantName,
            LocalDate receiptDate,
            BigDecimal amount,
            String currencyCode,
            String notes
    ) {
        this.userPolicyYear = userPolicyYear;
        this.reliefCategory = reliefCategory;
        this.merchantName = merchantName;
        this.receiptDate = receiptDate;
        this.amount = amount;
        this.currencyCode = currencyCode;
        this.notes = notes;
        this.status = ReceiptStatus.VERIFIED;
        this.processingErrorCode = null;
        this.processingErrorMessage = null;
    }

    public void rejectReview(String notes, String invalidReasonCode, String invalidReasonMessage) {
        this.notes = notes;
        this.status = ReceiptStatus.REJECTED;
        this.processingErrorCode = invalidReasonCode;
        this.processingErrorMessage = invalidReasonMessage;
    }

    public void markProcessing() {
        this.status = ReceiptStatus.PROCESSING;
        this.processingErrorCode = null;
        this.processingErrorMessage = null;
    }

    public void markProcessed() {
        this.status = ReceiptStatus.PROCESSED;
        this.processingErrorCode = null;
        this.processingErrorMessage = null;
    }

    public void markFailed(String errorCode, String errorMessage) {
        this.status = ReceiptStatus.FAILED;
        this.processingErrorCode = errorCode;
        this.processingErrorMessage = errorMessage;
    }

    public void updateManualDetails(
            UserPolicyYear userPolicyYear,
            ReliefCategory reliefCategory,
            String merchantName,
            LocalDate receiptDate,
            BigDecimal amount,
            String notes,
            String fileName,
            String fileUrl
    ) {
        this.userPolicyYear = userPolicyYear;
        this.reliefCategory = reliefCategory;
        this.merchantName = merchantName;
        this.receiptDate = receiptDate;
        this.amount = amount;
        this.notes = notes;
        this.fileName = fileName;
        this.fileUrl = fileUrl;
        this.status = ReceiptStatus.VERIFIED;
        this.processingErrorCode = null;
        this.processingErrorMessage = null;
    }

    public void replaceFile(
            String newS3Key,
            String newFileUrl,
            String newFileName,
            String newMimeType,
            long newFileSizeBytes,
            String newSha256Hash,
            OffsetDateTime newUploadedAt
    ) {
        this.s3Key = newS3Key;
        this.fileUrl = newFileUrl;
        this.fileName = newFileName;
        this.mimeType = newMimeType;
        this.fileSizeBytes = newFileSizeBytes;
        this.sha256Hash = newSha256Hash;
        this.uploadedAt = newUploadedAt;
    }

    public void assignFileUrl(String fileUrl) {
        this.fileUrl = fileUrl;
    }

    public UUID getId() {
        return id;
    }

    public UserPolicyYear getUserPolicyYear() {
        return userPolicyYear;
    }

    public Integer getPolicyYear() {
        return userPolicyYear.getPolicyYear().getYear();
    }

    public ReliefCategory getReliefCategory() {
        return reliefCategory;
    }

    public String getMerchantName() {
        return merchantName;
    }

    public LocalDate getReceiptDate() {
        return receiptDate;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrencyCode() {
        return currencyCode;
    }

    public String getNotes() {
        return notes;
    }

    public String getFileName() {
        return fileName;
    }

    public String getFileUrl() {
        return fileUrl;
    }

    public String getS3Bucket() {
        return s3Bucket;
    }

    public String getS3Key() {
        return s3Key;
    }

    public String getMimeType() {
        return mimeType;
    }

    public long getFileSizeBytes() {
        return fileSizeBytes;
    }

    public String getSha256Hash() {
        return sha256Hash;
    }

    public ReceiptStatus getStatus() {
        return status;
    }

    public String getProcessingErrorCode() {
        return processingErrorCode;
    }

    public String getProcessingErrorMessage() {
        return processingErrorMessage;
    }

    public OffsetDateTime getUploadedAt() {
        return uploadedAt;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}
