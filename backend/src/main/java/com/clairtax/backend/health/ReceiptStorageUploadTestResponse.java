package com.clairtax.backend.health;

public record ReceiptStorageUploadTestResponse(
        String status,
        String storageBackend,
        boolean usesS3Storage,
        String bucketName,
        String objectKey,
        String originalFileName,
        String mimeType,
        long uploadedSizeBytes,
        long storedSizeBytes,
        boolean existsAfterUpload,
        boolean cleanupAttempted,
        boolean cleanedUp,
        String cleanupError
) {
}
