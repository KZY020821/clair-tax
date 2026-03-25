package com.clairtax.backend.receipt.storage;

import org.springframework.context.annotation.Profile;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

@Service
@Profile("!local")
public class LocalReceiptStorageService implements ReceiptStorageService {

    private final Path storageDirectory;

    public LocalReceiptStorageService(ReceiptStorageProperties receiptStorageProperties) throws IOException {
        this.storageDirectory = Paths.get(receiptStorageProperties.getStoragePath()).toAbsolutePath().normalize();
        Files.createDirectories(storageDirectory);
    }

    @Override
    public void store(UUID receiptId, MultipartFile file) throws IOException {
        Files.copy(
                file.getInputStream(),
                storageDirectory.resolve(receiptId.toString()),
                StandardCopyOption.REPLACE_EXISTING
        );
    }

    @Override
    public Resource load(UUID receiptId) throws IOException {
        Path path = storageDirectory.resolve(receiptId.toString());
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
    public void delete(UUID receiptId) throws IOException {
        Files.deleteIfExists(storageDirectory.resolve(receiptId.toString()));
    }
}
