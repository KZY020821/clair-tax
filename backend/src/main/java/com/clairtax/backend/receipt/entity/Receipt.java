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

    @Column(name = "merchant_name", nullable = false)
    private String merchantName;

    @Column(name = "receipt_date", nullable = false)
    private LocalDate receiptDate;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "relief_category_id")
    private ReliefCategory reliefCategory;

    @Column
    private String notes;

    @Column(name = "file_name")
    private String fileName;

    @Column(name = "file_url")
    private String fileUrl;

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
            String merchantName,
            LocalDate receiptDate,
            BigDecimal amount,
            ReliefCategory reliefCategory,
            String notes,
            String fileName,
            String fileUrl
    ) {
        this.userPolicyYear = userPolicyYear;
        this.merchantName = merchantName;
        this.receiptDate = receiptDate;
        this.amount = amount;
        this.reliefCategory = reliefCategory;
        this.notes = notes;
        this.fileName = fileName;
        this.fileUrl = fileUrl;
    }

    public void update(
            UserPolicyYear userPolicyYear,
            String merchantName,
            LocalDate receiptDate,
            BigDecimal amount,
            ReliefCategory reliefCategory,
            String notes,
            String fileName,
            String fileUrl
    ) {
        this.userPolicyYear = userPolicyYear;
        this.merchantName = merchantName;
        this.receiptDate = receiptDate;
        this.amount = amount;
        this.reliefCategory = reliefCategory;
        this.notes = notes;
        this.fileName = fileName;
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

    public String getMerchantName() {
        return merchantName;
    }

    public LocalDate getReceiptDate() {
        return receiptDate;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public ReliefCategory getReliefCategory() {
        return reliefCategory;
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

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}
