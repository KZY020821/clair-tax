package com.clairtax.backend.receipt.storage;

import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.UUID;

public interface ReceiptStorageService {

    void store(UUID receiptId, MultipartFile file) throws IOException;

    Resource load(UUID receiptId) throws IOException;

    void delete(UUID receiptId) throws IOException;
}
