package com.clairtax.backend.receipt.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record ConfirmReceiptReviewRequest(
        String merchantName,
        LocalDate receiptDate,
        @DecimalMin("0.00") BigDecimal amount,
        @Size(min = 3, max = 3) String currency,
        UUID reliefCategoryId,
        @Size(max = 2000) String notes
) {
}
