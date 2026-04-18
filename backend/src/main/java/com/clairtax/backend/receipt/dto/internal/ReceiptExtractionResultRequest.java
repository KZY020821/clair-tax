package com.clairtax.backend.receipt.dto.internal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record ReceiptExtractionResultRequest(
        @NotNull UUID jobId,
        @DecimalMin("0.00") BigDecimal totalAmount,
        LocalDate receiptDate,
        @Size(max = 160) String merchantName,
        @Size(min = 3, max = 3) String currency,
        @NotNull @DecimalMin("0.00") BigDecimal confidenceScore,
        @NotNull List<@NotBlank String> warnings,
        @NotBlank String rawPayloadJson,
        @NotBlank String providerName,
        @NotBlank String providerVersion,
        @NotNull OffsetDateTime processedAt,
        @Size(max = 120) String errorCode,
        @Size(max = 2000) String errorMessage
) {
}
