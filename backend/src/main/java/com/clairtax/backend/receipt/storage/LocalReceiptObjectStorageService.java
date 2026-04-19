package com.clairtax.backend.receipt.storage;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.OffsetDateTime;
import java.util.Map;

@Service
@ConditionalOnMissingBean(ReceiptObjectStorageService.class)
public class LocalReceiptObjectStorageService implements ReceiptObjectStorageService {

    private final Path storagePath;

    public LocalReceiptObjectStorageService() {
        String path = System.getenv().getOrDefault("CLAIR_RECEIPTS_STORAGE_PATH", "/tmp/clair-tax-receipts");
        this.storagePath = Paths.get(path);
    }

    @Override
    public ReceiptUploadTarget createUploadTarget(String objectKey, String mimeType) {
        return new ReceiptUploadTarget("local://" + objectKey, "PUT", Map.of(), OffsetDateTime.now().plusMinutes(15));
    }

    @Override
    public void storeUploadedObject(String objectKey, InputStream inputStream) throws IOException {
        Path targetPath = resolvedPath(objectKey);
        Files.createDirectories(targetPath.getParent());
        Files.copy(inputStream, targetPath, StandardCopyOption.REPLACE_EXISTING);
    }

    @Override
    public Resource load(String objectKey) throws IOException {
        Path filePath = resolvedPath(objectKey);
        if (!Files.exists(filePath)) {
            throw new IOException("Receipt file not found locally: " + objectKey);
        }
        return new FileSystemResource(filePath);
    }

    @Override
    public boolean exists(String objectKey) {
        return Files.exists(resolvedPath(objectKey));
    }

    @Override
    public long size(String objectKey) throws IOException {
        return Files.size(resolvedPath(objectKey));
    }

    @Override
    public void delete(String objectKey) throws IOException {
        Files.deleteIfExists(resolvedPath(objectKey));
    }

    private Path resolvedPath(String objectKey) {
        // Flatten slashes so keys don't create unexpected subdirectories
        return storagePath.resolve(objectKey.replace('/', '_'));
    }
}
