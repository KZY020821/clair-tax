package com.clairtax.backend.receipt.queue;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ReceiptProcessingJobMessage(
        String schemaVersion,
        UUID jobId,
        UUID receiptId,
        UUID userId,
        Integer policyYear,
        UUID reliefCategoryId,
        String s3Bucket,
        String s3Key,
        String mimeType,
        long fileSizeBytes,
        String sha256Hash,
        OffsetDateTime uploadedAt,
        String correlationId
) {
}
