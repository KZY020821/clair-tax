package com.clairtax.backend.receipt.entity;

import com.clairtax.backend.receipt.model.ReceiptReviewActionType;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "receipt_review_actions")
public class ReceiptReviewAction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "receipt_id", nullable = false)
    private Receipt receipt;

    @Enumerated(EnumType.STRING)
    @Column(name = "action_type", nullable = false, length = 16)
    private ReceiptReviewActionType actionType;

    @Column(name = "merchant_name")
    private String merchantName;

    @Column(name = "receipt_date")
    private LocalDate receiptDate;

    @Column(name = "amount", precision = 14, scale = 2)
    private BigDecimal amount;

    @Column(name = "currency_code", length = 3)
    private String currencyCode;

    @Column(name = "notes", length = 2000)
    private String notes;

    @Column(name = "invalid_reason_code", length = 120)
    private String invalidReasonCode;

    @Column(name = "invalid_reason_message", length = 2000)
    private String invalidReasonMessage;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected ReceiptReviewAction() {
    }

    public ReceiptReviewAction(
            Receipt receipt,
            ReceiptReviewActionType actionType,
            String merchantName,
            LocalDate receiptDate,
            BigDecimal amount,
            String currencyCode,
            String notes,
            String invalidReasonCode,
            String invalidReasonMessage
    ) {
        this.receipt = receipt;
        this.actionType = actionType;
        this.merchantName = merchantName;
        this.receiptDate = receiptDate;
        this.amount = amount;
        this.currencyCode = currencyCode;
        this.notes = notes;
        this.invalidReasonCode = invalidReasonCode;
        this.invalidReasonMessage = invalidReasonMessage;
    }

    public ReceiptReviewActionType getActionType() {
        return actionType;
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

    public String getMerchantName() {
        return merchantName;
    }

    public String getInvalidReasonCode() {
        return invalidReasonCode;
    }

    public String getInvalidReasonMessage() {
        return invalidReasonMessage;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
