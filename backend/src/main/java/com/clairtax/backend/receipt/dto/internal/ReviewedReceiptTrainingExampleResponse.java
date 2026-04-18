package com.clairtax.backend.receipt.dto.internal;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public record ReviewedReceiptTrainingExampleResponse(
        UUID receiptId,
        Integer policyYear,
        String fileName,
        String fileUrl,
        String s3Bucket,
        String s3Key,
        String mimeType,
        long fileSizeBytes,
        boolean isValidReceipt,
        String invalidReason,
        String invalidReasonMessage,
        BigDecimal correctTotalAmount,
        LocalDate correctReceiptDate,
        String merchantName,
        String currency,
        String rawPayloadJson,
        String providerName,
        String providerVersion,
        String annotationSource,
        OffsetDateTime reviewedAt
) {
}
