package com.clairtax.backend.receipt.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "clair.receipts")
public class ReceiptStorageProperties {

    private String storagePath = "/tmp/clair-tax-receipts";

    public String getStoragePath() {
        return storagePath;
    }

    public void setStoragePath(String storagePath) {
        this.storagePath = storagePath;
    }
}
