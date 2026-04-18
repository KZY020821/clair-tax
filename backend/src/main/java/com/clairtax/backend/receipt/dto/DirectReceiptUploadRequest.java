package com.clairtax.backend.receipt.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record DirectReceiptUploadRequest(
        @NotBlank @Size(max = 160) String merchantName,
        @NotNull LocalDate receiptDate,
        @NotNull @DecimalMin("0.01") BigDecimal amount,
        UUID reliefCategoryId,
        @Size(max = 2000) String notes
) {
}
