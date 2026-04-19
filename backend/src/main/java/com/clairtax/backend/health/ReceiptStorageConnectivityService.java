package com.clairtax.backend.health;

import com.clairtax.backend.calculator.exception.CalculatorValidationException;
import com.clairtax.backend.receipt.config.ReceiptProcessingProperties;
import com.clairtax.backend.receipt.storage.AwsReceiptObjectStorageService;
import com.clairtax.backend.receipt.storage.ReceiptObjectStorageService;
import org.springframework.stereotype.Service;
import org.springframework.util.ClassUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Service
public class ReceiptStorageConnectivityService {

    private static final long MAX_FILE_SIZE_BYTES = 10L * 1024L * 1024L;

    private final ReceiptObjectStorageService receiptObjectStorageService;
    private final ReceiptProcessingProperties properties;

    public ReceiptStorageConnectivityService(
            ReceiptObjectStorageService receiptObjectStorageService,
            ReceiptProcessingProperties properties
    ) {
        this.receiptObjectStorageService = receiptObjectStorageService;
        this.properties = properties;
    }

    public ReceiptStorageUploadTestResponse uploadTestFile(MultipartFile file, boolean keepFile) {
        validateFile(file);

        String originalFileName = normalizeFileName(file.getOriginalFilename());
        String mimeType = normalizeMimeType(file.getContentType());
        String objectKey = buildObjectKey(originalFileName);
        Class<?> storageClass = ClassUtils.getUserClass(receiptObjectStorageService);
        boolean cleanupAttempted = !keepFile;
        boolean cleanedUp = false;
        String cleanupError = null;

        try (InputStream inputStream = file.getInputStream()) {
            receiptObjectStorageService.storeUploadedObject(objectKey, inputStream);

            boolean existsAfterUpload = receiptObjectStorageService.exists(objectKey);
            long storedSizeBytes = receiptObjectStorageService.size(objectKey);
            if (!existsAfterUpload) {
                throw new IllegalStateException("Receipt storage reported that the uploaded test object does not exist");
            }

            if (!keepFile) {
                CleanupResult cleanupResult = tryCleanup(objectKey);
                cleanedUp = cleanupResult.cleanedUp();
                cleanupError = cleanupResult.cleanupError();
            }

            return new ReceiptStorageUploadTestResponse(
                    "ok",
                    storageClass.getName(),
                    AwsReceiptObjectStorageService.class.isAssignableFrom(storageClass),
                    properties.getBucketName(),
                    objectKey,
                    originalFileName,
                    mimeType,
                    file.getSize(),
                    storedSizeBytes,
                    existsAfterUpload,
                    cleanupAttempted,
                    cleanedUp,
                    cleanupError
            );
        } catch (IOException exception) {
            if (!keepFile) {
                tryCleanup(objectKey);
            }
            throw new IllegalStateException("Failed to upload the test file to receipt storage", exception);
        }
    }

    private void validateFile(MultipartFile file) {
        if (file.isEmpty()) {
            throw new CalculatorValidationException("A non-empty file is required for the storage upload test");
        }
        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            throw new CalculatorValidationException("Test file is too large. Please upload a file smaller than 10MB.");
        }
    }

    private String normalizeFileName(String originalFileName) {
        if (originalFileName == null || originalFileName.isBlank()) {
            return "upload-test.bin";
        }
        return originalFileName.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    private String normalizeMimeType(String mimeType) {
        if (mimeType == null || mimeType.isBlank()) {
            return "application/octet-stream";
        }
        return mimeType.trim();
    }

    private String buildObjectKey(String originalFileName) {
        String timestamp = OffsetDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        return "connectivity-tests/%s-%s-%s".formatted(timestamp, UUID.randomUUID(), originalFileName);
    }

    private CleanupResult tryCleanup(String objectKey) {
        try {
            receiptObjectStorageService.delete(objectKey);
            boolean existsAfterCleanup = receiptObjectStorageService.exists(objectKey);
            return new CleanupResult(!existsAfterCleanup, existsAfterCleanup ? "Uploaded test object still exists after cleanup" : null);
        } catch (IOException exception) {
            return new CleanupResult(false, exception.getMessage());
        }
    }

    private record CleanupResult(boolean cleanedUp, String cleanupError) {
    }
}
