package com.clairtax.backend.receipt.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public class UploadYearReceiptRequest {

    @NotBlank
    @Size(max = 160)
    private String merchantName;

    @NotNull
    private LocalDate receiptDate;

    @NotNull
    @DecimalMin("0.00")
    private BigDecimal amount;

    @NotNull
    private UUID reliefCategoryId;

    @Size(max = 2000)
    private String notes;

    @NotNull
    private MultipartFile file;

    public String getMerchantName() {
        return merchantName;
    }

    public void setMerchantName(String merchantName) {
        this.merchantName = merchantName;
    }

    public LocalDate getReceiptDate() {
        return receiptDate;
    }

    public void setReceiptDate(LocalDate receiptDate) {
        this.receiptDate = receiptDate;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public UUID getReliefCategoryId() {
        return reliefCategoryId;
    }

    public void setReliefCategoryId(UUID reliefCategoryId) {
        this.reliefCategoryId = reliefCategoryId;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public MultipartFile getFile() {
        return file;
    }

    public void setFile(MultipartFile file) {
        this.file = file;
    }
}
