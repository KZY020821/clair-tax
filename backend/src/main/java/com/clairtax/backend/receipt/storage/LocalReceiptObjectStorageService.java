package com.clairtax.backend.receipt.storage;

import com.clairtax.backend.receipt.config.ReceiptProcessingProperties;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.OffsetDateTime;
import java.util.Map;

@Service
@Profile({"test", "postgres"})
public class LocalReceiptObjectStorageService implements ReceiptObjectStorageService {

    private final Path storageDirectory;
    private final Path uploadDirectory;

    public LocalReceiptObjectStorageService(ReceiptProcessingProperties properties) throws IOException {
        this.storageDirectory = Paths.get(properties.getStoragePath()).toAbsolutePath().normalize();
        this.uploadDirectory = Paths.get(properties.getUploadPath()).toAbsolutePath().normalize();
        Files.createDirectories(storageDirectory);
        Files.createDirectories(uploadDirectory);
    }

    @Override
    public ReceiptUploadTarget createUploadTarget(String objectKey, String mimeType) {
        return new ReceiptUploadTarget(
                "/api/internal/receipt-uploads/" + objectKey,
                "PUT",
                Map.of("Content-Type", mimeType),
                OffsetDateTime.now().plusMinutes(15)
        );
    }

    @Override
    public void storeUploadedObject(String objectKey, InputStream inputStream) throws IOException {
        Path path = resolvePath(uploadDirectory, objectKey);
        Files.createDirectories(path.getParent());
        Files.copy(inputStream, path, StandardCopyOption.REPLACE_EXISTING);
    }

    @Override
    public Resource load(String objectKey) throws IOException {
        Path path = resolvePath(storageDirectory, objectKey);
        if (!Files.exists(path)) {
            throw new IOException("Stored receipt file was not found");
        }

        try {
            return new UrlResource(path.toUri());
        } catch (MalformedURLException exception) {
            throw new IOException("Stored receipt file could not be resolved", exception);
        }
    }

    @Override
    public boolean exists(String objectKey) {
        return Files.exists(resolvePath(uploadDirectory, objectKey))
                || Files.exists(resolvePath(storageDirectory, objectKey));
    }

    @Override
    public long size(String objectKey) throws IOException {
        Path uploadPath = resolvePath(uploadDirectory, objectKey);
        if (Files.exists(uploadPath)) {
            return Files.size(uploadPath);
        }

        return Files.size(resolvePath(storageDirectory, objectKey));
    }

    @Override
    public void delete(String objectKey) throws IOException {
        Files.deleteIfExists(resolvePath(storageDirectory, objectKey));
        Files.deleteIfExists(resolvePath(uploadDirectory, objectKey));
    }

    public void promoteUploadedObject(String objectKey) throws IOException {
        Path uploadPath = resolvePath(uploadDirectory, objectKey);
        Path storagePath = resolvePath(storageDirectory, objectKey);
        Files.createDirectories(storagePath.getParent());
        Files.move(uploadPath, storagePath, StandardCopyOption.REPLACE_EXISTING);
    }

    public InputStream openStoredObject(String objectKey) throws IOException {
        Path uploadPath = resolvePath(uploadDirectory, objectKey);
        Path existingPath = Files.exists(uploadPath) ? uploadPath : resolvePath(storageDirectory, objectKey);
        return Files.newInputStream(existingPath);
    }

    private Path resolvePath(Path root, String objectKey) {
        return root.resolve(objectKey).normalize();
    }
}
