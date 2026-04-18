package com.clairtax.backend.receipt.entity;

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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "receipt_extraction_results",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_receipt_extraction_results_job_id", columnNames = {"job_id"})
        }
)
public class ReceiptExtractionResult {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "receipt_id", nullable = false)
    private Receipt receipt;

    @Column(name = "job_id", nullable = false)
    private UUID jobId;

    @Column(name = "total_amount", precision = 14, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "receipt_date")
    private LocalDate receiptDate;

    @Column(name = "merchant_name")
    private String merchantName;

    @Column(name = "currency_code", length = 3)
    private String currencyCode;

    @Column(name = "confidence_score", nullable = false, precision = 5, scale = 4)
    private BigDecimal confidenceScore;

    @Column(name = "warning_messages", nullable = false, length = 4000)
    private String warningMessages;

    @Column(name = "raw_payload_json", nullable = false, length = 20000)
    private String rawPayloadJson;

    @Column(name = "provider_name", nullable = false)
    private String providerName;

    @Column(name = "provider_version", nullable = false)
    private String providerVersion;

    @Column(name = "processed_at", nullable = false)
    private OffsetDateTime processedAt;

    @Column(name = "error_code", length = 120)
    private String errorCode;

    @Column(name = "error_message", length = 2000)
    private String errorMessage;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected ReceiptExtractionResult() {
    }

    public ReceiptExtractionResult(
            Receipt receipt,
            UUID jobId,
            BigDecimal totalAmount,
            LocalDate receiptDate,
            String merchantName,
            String currencyCode,
            BigDecimal confidenceScore,
            String warningMessages,
            String rawPayloadJson,
            String providerName,
            String providerVersion,
            OffsetDateTime processedAt,
            String errorCode,
            String errorMessage
    ) {
        this.receipt = receipt;
        this.jobId = jobId;
        this.totalAmount = totalAmount;
        this.receiptDate = receiptDate;
        this.merchantName = merchantName;
        this.currencyCode = currencyCode;
        this.confidenceScore = confidenceScore;
        this.warningMessages = warningMessages;
        this.rawPayloadJson = rawPayloadJson;
        this.providerName = providerName;
        this.providerVersion = providerVersion;
        this.processedAt = processedAt;
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
    }

    public UUID getId() {
        return id;
    }

    public Receipt getReceipt() {
        return receipt;
    }

    public UUID getJobId() {
        return jobId;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public LocalDate getReceiptDate() {
        return receiptDate;
    }

    public String getMerchantName() {
        return merchantName;
    }

    public String getCurrencyCode() {
        return currencyCode;
    }

    public BigDecimal getConfidenceScore() {
        return confidenceScore;
    }

    public String getWarningMessages() {
        return warningMessages;
    }

    public String getRawPayloadJson() {
        return rawPayloadJson;
    }

    public String getProviderName() {
        return providerName;
    }

    public String getProviderVersion() {
        return providerVersion;
    }

    public OffsetDateTime getProcessedAt() {
        return processedAt;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public String getErrorMessage() {
        return errorMessage;
    }
}
