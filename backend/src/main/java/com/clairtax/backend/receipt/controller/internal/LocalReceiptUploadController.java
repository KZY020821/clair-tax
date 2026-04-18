package com.clairtax.backend.receipt.controller.internal;

import com.clairtax.backend.receipt.storage.LocalReceiptObjectStorageService;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.ByteArrayInputStream;
import java.io.IOException;

@RestController
@Profile({"test", "postgres"})
@RequestMapping("/api/internal/receipt-uploads")
public class LocalReceiptUploadController {

    private final LocalReceiptObjectStorageService localReceiptObjectStorageService;

    public LocalReceiptUploadController(LocalReceiptObjectStorageService localReceiptObjectStorageService) {
        this.localReceiptObjectStorageService = localReceiptObjectStorageService;
    }

    @PutMapping("/{objectKey:.+}")
    public ResponseEntity<Void> upload(
            @PathVariable String objectKey,
            @RequestBody byte[] body
    ) throws IOException {
        localReceiptObjectStorageService.storeUploadedObject(objectKey, new ByteArrayInputStream(body));
        return ResponseEntity.noContent().build();
    }
}
