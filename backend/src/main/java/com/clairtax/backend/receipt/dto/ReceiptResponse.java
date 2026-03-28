package com.clairtax.backend.receipt.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public record ReceiptResponse(
        UUID id,
        Integer policyYear,
        String merchantName,
        LocalDate receiptDate,
        BigDecimal amount,
        String currency,
        UUID reliefCategoryId,
        String reliefCategoryName,
        String notes,
        String fileName,
        String fileUrl,
        String mimeType,
        long fileSizeBytes,
        String status,
        String processingErrorCode,
        String processingErrorMessage,
        ReceiptLatestExtractionResponse latestExtraction,
        OffsetDateTime uploadedAt,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
