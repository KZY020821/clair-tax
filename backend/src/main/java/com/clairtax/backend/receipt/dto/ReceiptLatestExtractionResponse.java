package com.clairtax.backend.receipt.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

public record ReceiptLatestExtractionResponse(
        BigDecimal totalAmount,
        LocalDate receiptDate,
        String merchantName,
        String currency,
        BigDecimal confidenceScore,
        List<String> warnings,
        String providerName,
        String providerVersion,
        OffsetDateTime processedAt,
        String errorCode,
        String errorMessage
) {
}
