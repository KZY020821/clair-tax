package com.clairtax.backend.receipt.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Pattern;
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
        @Size(max = 2000) String notes,
        @Size(max = 36) String einvoiceUuid,
        @Size(max = 64) String einvoiceNumber,
        @Pattern(
                regexp = "^[A-Z]{1,2}[0-9]{10,11}$",
                message = "Supplier TIN must begin with 1–2 uppercase letters followed by 10–11 digits"
        )
        String supplierTin
) {
}
